package jdbox.openedfiles;

import com.google.inject.Inject;
import jdbox.DriveAdapter;
import jdbox.Uploader;
import jdbox.filetree.File;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;

class FullAccessOpenedFile implements ByteStore {

    private static final Logger logger = LoggerFactory.getLogger(InMemoryByteStore.class);

    private final ByteBuffer oneByteBuffer = ByteBuffer.wrap(new byte[1]);

    private ByteStore content;
    private ByteSource reader;

    FullAccessOpenedFile(ByteStore content, Future<InputStream> stream, StreamCachingByteSourceFactory readerFactory) {
        this.content = content;
        this.reader = stream != null ? readerFactory.create(stream, content) : null;
    }

    @Override
    public synchronized int read(ByteBuffer buffer, long offset, int count) throws IOException {

        logger.debug("read, offset {}, count {}", offset, count);

        if (content == null)
            throw new IOException("read on a closed ByteStore");

        if (reader == null)
            return content.read(buffer, offset, count);

        return reader.read(buffer, offset, count);
    }

    @Override
    public synchronized int write(ByteBuffer buffer, long offset, int count) throws IOException {

        logger.debug("write, offset {}, count {}", offset, count);

        if (content == null)
            throw new IOException("write on a closed ByteStore");

        ensureContentIsRead(offset + count);

        return content.write(buffer, offset, count);
    }

    @Override
    public synchronized void truncate(long length) throws IOException {

        logger.debug("truncate, length {}", length);

        ensureContentIsRead(length);
        closeReader();

        content.truncate(length);
    }

    @Override
    public synchronized void close() throws IOException {

        if (content == null)
            return;

        content = null;

        closeReader();
    }

    private void closeReader() throws IOException {

        if (reader == null)
            return;

        reader.close();
        reader = null;
    }

    private void ensureContentIsRead(long offset) throws IOException {
        if (reader != null) {
            oneByteBuffer.rewind();
            reader.read(oneByteBuffer, offset - 1, 1);
        }
    }
}

class FullAccessOpenedFileFactory implements OpenedFileFactory {

    private static final Logger logger = LoggerFactory.getLogger(FullAccessOpenedFileFactory.class);

    private final DriveAdapter drive;
    private final InMemoryByteStoreFactory storeFactory;
    private final StreamCachingByteSourceFactory readerFactory;
    private final Uploader uploader;

    private final Map<File, SharedOpenedFile> sharedFiles = new HashMap<>();

    @Inject
    FullAccessOpenedFileFactory(
            DriveAdapter drive, InMemoryByteStoreFactory storeFactory,
            StreamCachingByteSourceFactory readerFactory, Uploader uploader) {
        this.drive = drive;
        this.storeFactory = storeFactory;
        this.readerFactory = readerFactory;
        this.uploader = uploader;
    }

    public synchronized int getSharedFilesCount() {
        return sharedFiles.size();
    }

    @Override
    public synchronized ByteStore create(final File file) {

        SharedOpenedFile sharedFile = sharedFiles.get(file);

        ByteStore openedFile;

        if (sharedFile != null) {
            sharedFile.refCount++;
        } else {
            Future<InputStream> stream =
                    file.isUploaded() ? drive.downloadFileRangeAsync(file, 0, file.getSize()) : null;
            openedFile = new FullAccessOpenedFile(storeFactory.create(), stream, readerFactory);
            sharedFile = new SharedOpenedFile(file, openedFile);
            sharedFiles.put(file, sharedFile);
        }

        return new ProxyByteStore(sharedFile);
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
                        sharedFiles.remove(shared.file).content.close();

                    return;
                }

                final ByteStore capturedContent = storeFactory.create();
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
                                    sharedFiles.remove(shared.file).content.close();
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
