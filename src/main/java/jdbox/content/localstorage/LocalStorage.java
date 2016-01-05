package jdbox.content.localstorage;

import com.google.inject.Inject;
import jdbox.content.bytestores.ByteSources;
import jdbox.content.bytestores.ByteStore;
import jdbox.content.bytestores.InMemoryByteStoreFactory;
import jdbox.driveadapter.DriveAdapter;
import jdbox.driveadapter.Field;
import jdbox.localstate.LocalState;
import jdbox.localstate.LocalUpdateSafe;
import jdbox.localstate.knownfiles.KnownFile;
import jdbox.localstate.knownfiles.KnownFiles;
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
    private final LocalState localState;
    private final Map<FileId, SharedOpenedFile> files = new HashMap<>();

    @Inject
    LocalStorage(
            DriveAdapter drive, InMemoryByteStoreFactory tempStoreFactory,
            Observer<FileSizeUpdateEvent> fileSizeUpdateEvent, LocalState localState) {
        this.drive = drive;
        this.tempStoreFactory = tempStoreFactory;
        this.fileSizeUpdateEvent = fileSizeUpdateEvent;
        this.localState = localState;
    }

    public void reset() {
        files.clear();
    }

    public synchronized int getFilesCount() {
        return files.size();
    }

    public synchronized Long getSize(FileId fileId) {
        SharedOpenedFile shared = files.get(fileId);
        if (shared == null)
            return null;
        return files.get(fileId).content.getSize();
    }

    public synchronized ByteStore getContent(FileId fileId) {

        SharedOpenedFile shared = files.get(fileId);

        if (shared == null)
            return null;

        shared.refCount++;

        return new ContentUpdatingProxyOpenedFile(shared);
    }

    public synchronized ByteStore putContent(FileId fileId, ByteStore content) {

        SharedOpenedFile shared = new SharedOpenedFile(fileId, content);

        files.put(fileId, shared);

        shared.refCount++;

        return new ContentUpdatingProxyOpenedFile(shared);
    }

    private class SharedOpenedFile {

        public final FileId fileId;
        public final ByteStore content;
        public volatile int refCount = 0;

        private SharedOpenedFile(FileId fileId, ByteStore content) {
            this.fileId = fileId;
            this.content = content;
        }
    }

    private class ContentUpdatingProxyOpenedFile implements ByteStore {

        private final SharedOpenedFile shared;
        private boolean hasChanged = false;

        // this flag is used to make sure that every instance is closed only once and
        // therefore refcount is decreased only once for each instance
        private boolean closed = false;

        public ContentUpdatingProxyOpenedFile(SharedOpenedFile shared) {
            this.shared = shared;
        }

        @Override
        public synchronized int read(ByteBuffer buffer, long offset, int count) throws IOException {

            if (closed)
                throw new IOException("read on a closed ByteStore");

            return shared.content.read(buffer, offset, count);
        }

        @Override
        public synchronized int write(ByteBuffer buffer, long offset, int count) throws IOException {

            if (closed)
                throw new IOException("write on a closed ByteStore");

            hasChanged = true;
            return shared.content.write(buffer, offset, count);
        }

        @Override
        public synchronized void truncate(long offset) throws IOException {

            if (closed)
                throw new IOException("truncate on a closed ByteStore");

            hasChanged = true;
            shared.content.truncate(offset);
        }

        @Override
        public long getSize() {
            return shared.content.getSize();
        }

        @Override
        public synchronized void close() throws IOException {

            logger.debug("closing a proxy, has changed {}, ref count {}", hasChanged, shared.refCount);

            if (closed)
                return;

            closed = true;

            if (!hasChanged) {

                synchronized (LocalStorage.this) {
                    assert shared.refCount > 0;
                    shared.refCount--;
                    if (shared.refCount == 0)
                        files.remove(shared.fileId).content.close();
                }

                return;
            }

            final ByteStore capturedContent = tempStoreFactory.create();
            final int size = ByteSources.copy(shared.content, capturedContent);

            localState.update(new LocalUpdateSafe() {
                @Override
                public Void run(KnownFiles knownFiles, Uploader uploader) {

                    KnownFile existing = knownFiles.get(shared.fileId);
                    assert existing != null;

                    uploader.submit(new DriveTask(
                            "update content, content length is " + size,
                            existing.toFile(), EnumSet.noneOf(Field.class)) {
                        @Override
                        public jdbox.driveadapter.File run(jdbox.driveadapter.File file) throws IOException {

                            jdbox.driveadapter.File updatedFile =
                                    drive.updateFileContent(file, ByteSources.toInputStream(capturedContent));

                            capturedContent.close();

                            synchronized (LocalStorage.this) {
                                assert shared.refCount > 0;
                                shared.refCount--;
                                if (shared.refCount == 0) {
                                    fileSizeUpdateEvent.onNext(new FileSizeUpdateEvent(shared.fileId, size));
                                    files.remove(shared.fileId).content.close();
                                }
                            }

                            return updatedFile;
                        }
                    });
                    return null;
                }
            });
        }
    }
}
