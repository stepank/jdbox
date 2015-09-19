package jdbox.openedfiles;

import com.google.inject.Inject;
import jdbox.driveadapter.DriveAdapter;
import jdbox.models.File;
import jdbox.models.fileids.FileId;
import jdbox.uploader.Task;
import jdbox.uploader.Uploader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

public class LocalStorage {

    private static final Logger logger = LoggerFactory.getLogger(LocalStorage.class);

    private final DriveAdapter drive;
    private final InMemoryByteStoreFactory tempStoreFactory;
    private final Observer<FileSizeUpdateEvent> fileSizeUpdateEvent;
    private final Uploader uploader;
    private final Map<FileId, SharedOpenedFile> files = new HashMap<>();

    @Inject
    LocalStorage(
            DriveAdapter drive, InMemoryByteStoreFactory tempStoreFactory,
            Observer<FileSizeUpdateEvent> fileSizeUpdateEvent, Uploader uploader) {
        this.drive = drive;
        this.tempStoreFactory = tempStoreFactory;
        this.fileSizeUpdateEvent = fileSizeUpdateEvent;
        this.uploader = uploader;
    }

    public void reset() {
        files.clear();
    }

    public synchronized int getFilesCount() {
        return files.size();
    }

    public synchronized Long getSize(File file) {
        SharedOpenedFile shared = files.get(file.getId());
        if (shared == null)
            return null;
        return files.get(file.getId()).file.getSize();
    }

    public synchronized ByteStore getContent(File file) {

        SharedOpenedFile shared = files.get(file.getId());

        if (shared == null)
            return null;

        shared.refCount++;

        return new ContentUpdatingProxyOpenedFile(shared);
    }

    public synchronized ByteStore putContent(File file, ByteStore content) {

        SharedOpenedFile shared = new SharedOpenedFile(file, content);

        files.put(file.getId(), shared);

        return new ContentUpdatingProxyOpenedFile(shared);
    }

    private class SharedOpenedFile {

        public final File file;
        public final ByteStore content;
        public volatile int refCount = 1;
        public volatile boolean hasChanged = false;

        private SharedOpenedFile(File file, ByteStore content) {
            this.file = file;
            this.content = content;
        }
    }

    private class ContentUpdatingProxyOpenedFile implements ByteStore {

        private final SharedOpenedFile shared;
        private boolean closed = false;

        public ContentUpdatingProxyOpenedFile(SharedOpenedFile shared) {
            this.shared = shared;
        }

        @Override
        public int read(ByteBuffer buffer, long offset, int count) throws IOException {

            synchronized (shared) {

                if (closed)
                    throw new IOException("read on a closed ByteStore");

                return shared.content.read(buffer, offset, count);
            }
        }

        @Override
        public int write(ByteBuffer buffer, long offset, int count) throws IOException {

            synchronized (shared) {

                if (closed)
                    throw new IOException("write on a closed ByteStore");

                int written = shared.content.write(buffer, offset, count);

                if (written > 0) {
                    shared.hasChanged = true;
                    if (shared.file.getSize() < offset + written)
                        shared.file.setSize(offset + written);
                }

                return written;
            }
        }

        @Override
        public void truncate(long offset) throws IOException {

            synchronized (shared) {

                if (closed)
                    throw new IOException("truncate on a closed ByteStore");

                shared.hasChanged = true;
                shared.file.setSize(offset);

                shared.content.truncate(offset);
            }
        }

        @Override
        public void close() throws IOException {

            logger.debug(
                    "closing a proxy for {}, has changed {}, ref count {}",
                    shared.file, shared.hasChanged, shared.refCount);

            synchronized (shared) {

                if (closed)
                    return;

                if (!shared.hasChanged) {

                    assert shared.refCount > 0;

                    shared.refCount--;
                    if (shared.refCount == 0)
                        synchronized (LocalStorage.this) {
                            files.remove(shared.file.getId()).content.close();
                        }

                    return;
                }

                final ByteStore capturedContent = tempStoreFactory.create();
                final int size = ByteSources.copy(shared.content, capturedContent);

                shared.file.setSize(size);

                String label = shared.file.getName() + ": update content, content length is " + size;

                uploader.submit(new Task(label, shared.file.getId()) {
                    @Override
                    public void run() throws Exception {

                        drive.updateFileContent(shared.file.toDaFile(), ByteSources.toInputStream(capturedContent));

                        capturedContent.close();

                        synchronized (shared) {
                            shared.refCount--;
                            if (shared.refCount == 0)
                                synchronized (LocalStorage.this) {
                                    fileSizeUpdateEvent.onNext(
                                            new FileSizeUpdateEvent(shared.file.getId(), size));
                                    files.remove(shared.file.getId()).content.close();
                                }
                        }
                    }
                });
            }
        }
    }
}
