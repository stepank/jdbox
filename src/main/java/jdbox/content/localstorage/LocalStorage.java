package jdbox.content.localstorage;

import com.google.inject.Inject;
import jdbox.content.bytestores.ByteSources;
import jdbox.content.bytestores.ByteStore;
import jdbox.content.bytestores.InMemoryByteStoreFactory;
import jdbox.driveadapter.DriveAdapter;
import jdbox.driveadapter.Field;
import jdbox.localstate.LocalState;
import jdbox.localstate.interfaces.LocalUpdate;
import jdbox.localstate.knownfiles.KnownFile;
import jdbox.localstate.knownfiles.KnownFiles;
import jdbox.models.File;
import jdbox.models.fileids.FileId;
import jdbox.models.fileids.FileIdStore;
import jdbox.datapersist.ChangeSet;
import jdbox.uploader.DriveTask;
import jdbox.uploader.Task;
import jdbox.uploader.TaskDeserializer;
import jdbox.uploader.Uploader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

public class LocalStorage implements TaskDeserializer {

    private static final Logger logger = LoggerFactory.getLogger(LocalStorage.class);

    private final FileIdStore fileIdStore;
    private final DriveAdapter drive;
    private final InMemoryByteStoreFactory tempStoreFactory;
    private final LocalState localState;
    private final Map<FileId, SharedOpenedFile> files = new HashMap<>();

    @Inject
    LocalStorage(
            FileIdStore fileIdStore, DriveAdapter drive,
            InMemoryByteStoreFactory tempStoreFactory, LocalState localState) {
        this.fileIdStore = fileIdStore;
        this.drive = drive;
        this.tempStoreFactory = tempStoreFactory;
        this.localState = localState;
    }

    public void reset() {
        files.clear();
    }

    public int getFilesCount() {
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

    @Override
    public Task deserialize(String data) {
        return null;
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

            localState.update(new LocalUpdate() {
                @Override
                public Void run(ChangeSet changeSet, KnownFiles knownFiles, Uploader uploader) throws IOException {

                    MessageDigest digest;
                    try {
                        digest = MessageDigest.getInstance("MD5");
                    } catch (NoSuchAlgorithmException e) {
                        throw new RuntimeException(e);
                    }

                    final InputStream inputStream = ByteSources.toInputStream(capturedContent);

                    byte[] buffer = new byte[1024];
                    int read;
                    do {
                        read = inputStream.read(buffer);
                        if (read > 0)
                            digest.update(buffer, 0, read);
                    } while (read != -1);

                    inputStream.reset();

                    KnownFile existing = knownFiles.get(shared.fileId);
                    assert existing != null;
                    File original = existing.toFile();

                    existing.setContentProperties(changeSet, size, toHex(digest.digest()));

                    uploader.submit(changeSet, new DriveTask(
                            fileIdStore, drive, "update content, content length is " + size,
                            original, existing.toFile(), EnumSet.noneOf(Field.class)) {
                        @Override
                        public jdbox.driveadapter.File run(
                                ChangeSet changeSet, jdbox.driveadapter.File file) throws IOException {

                            jdbox.driveadapter.File updatedFile = drive.updateFileContent(file, inputStream);

                            capturedContent.close();

                            synchronized (LocalStorage.this) {
                                assert shared.refCount > 0;
                                shared.refCount--;
                                if (shared.refCount == 0) {
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

    private static String toHex(byte[] value) {
        return String.format("%0" + (value.length << 1) + "x", new BigInteger(1, value));
    }
}
