package jdbox.content.localstorage;

import com.google.inject.Inject;
import jdbox.content.bytestores.ByteSources;
import jdbox.content.bytestores.ByteStore;
import jdbox.content.bytestores.InMemoryByteStoreFactory;
import jdbox.driveadapter.DriveAdapter;
import jdbox.driveadapter.Field;
import jdbox.models.File;
import jdbox.models.fileids.FileId;
import jdbox.uploader.DriveTask;
import jdbox.uploader.Uploader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.EnumSet;
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
        return files.get(file.getId()).content.getSize();
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

                shared.hasChanged = true;
                return shared.content.write(buffer, offset, count);
            }
        }

        @Override
        public void truncate(long offset) throws IOException {

            synchronized (shared) {

                if (closed)
                    throw new IOException("truncate on a closed ByteStore");

                shared.hasChanged = true;
                shared.content.truncate(offset);
            }
        }

        @Override
        public long getSize() {
            synchronized (shared) {
                return shared.content.getSize();
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

                String label = shared.file.getName() + ": update content, content length is " + size;

                uploader.submit(new DriveTask(label, shared.file, EnumSet.noneOf(Field.class)) {
                    @Override
                    public jdbox.driveadapter.File run(jdbox.driveadapter.File file) throws IOException {

                        jdbox.driveadapter.File updatedFile =
                                drive.updateFileContent(file, ByteSources.toInputStream(capturedContent));

                        capturedContent.close();

                        synchronized (shared) {
                            shared.refCount--;
                            if (shared.refCount == 0)
                                synchronized (LocalStorage.this) {
                                    fileSizeUpdateEvent.onNext(new FileSizeUpdateEvent(shared.file.getId(), size));
                                    files.remove(shared.file.getId()).content.close();
                                }
                        }

                        return updatedFile;
                    }
                });
            }
        }
    }
}
