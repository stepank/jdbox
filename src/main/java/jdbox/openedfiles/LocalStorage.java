package jdbox.openedfiles;

import com.google.inject.Inject;
import jdbox.DriveAdapter;
import jdbox.Uploader;
import jdbox.filetree.File;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

public class LocalStorage {

    private static final Logger logger = LoggerFactory.getLogger(LocalStorage.class);

    private final DriveAdapter drive;
    private final InMemoryByteStoreFactory tempStoreFactory;
    private final Uploader uploader;
    private final Map<File, SharedOpenedFile> files = new HashMap<>();

    @Inject
    LocalStorage(DriveAdapter drive, InMemoryByteStoreFactory tempStoreFactory, Uploader uploader) {
        this.drive = drive;
        this.tempStoreFactory = tempStoreFactory;
        this.uploader = uploader;
    }

    public synchronized int getFilesCount() {
        return files.size();
    }

    public synchronized ByteStore getContent(File file) {

        SharedOpenedFile shared = files.get(file);

        if (shared == null)
            return null;

        shared.refCount++;

        return new ProxyByteStore(shared);
    }

    public synchronized ByteStore putContent(File file, ByteStore content) {

        SharedOpenedFile shared = new SharedOpenedFile(file, content);

        files.put(file, shared);

        return new ProxyByteStore(shared);
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

    private class ProxyByteStore implements ByteStore {

        private final SharedOpenedFile shared;
        private boolean closed = false;

        public ProxyByteStore(SharedOpenedFile shared) {
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
                        files.remove(shared.file).content.close();

                    return;
                }

                final ByteStore capturedContent = tempStoreFactory.create();
                ByteSources.copy(shared.content, capturedContent);

                uploader.submit(new Runnable() {
                    @Override
                    public void run() {
                        try {

                            shared.file.update(
                                    drive.updateFileContent(shared.file, ByteSources.toInputStream(capturedContent)));

                            capturedContent.close();

                            synchronized (shared) {
                                shared.refCount--;
                                if (shared.refCount == 0)
                                    files.remove(shared.file).content.close();
                            }

                        } catch (Exception e) {
                            logger.error("an error ocured while updating file content", e);
                        }
                    }
                });
            }
        }
    }
}
