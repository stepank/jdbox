package jdbox.openedfiles;

import com.google.inject.Inject;
import jdbox.driveadapter.DriveAdapter;
import jdbox.driveadapter.Field;
import jdbox.models.File;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.EnumSet;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;

class FullAccessOpenedFile implements ByteStore {

    private static final Logger logger = LoggerFactory.getLogger(FullAccessOpenedFile.class);

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
        if (reader != null && offset > 0) {
            oneByteBuffer.rewind();
            reader.read(oneByteBuffer, offset - 1, 1);
        }
    }
}

class FullAccessOpenedFileFactory implements ByteStoreFactory {

    private final DriveAdapter drive;
    private final InMemoryByteStoreFactory tempStoreFactory;
    private final StreamCachingByteSourceFactory readerFactory;
    private final Executor executor;

    @Inject
    FullAccessOpenedFileFactory(
            DriveAdapter drive, InMemoryByteStoreFactory tempStoreFactory,
            StreamCachingByteSourceFactory readerFactory, @PackagePrivate Executor executor) {
        this.drive = drive;
        this.tempStoreFactory = tempStoreFactory;
        this.readerFactory = readerFactory;
        this.executor = executor;
    }

    @Override
    public long getSize(File file) {
        return file.getSize();
    }

    @Override
    public synchronized ByteStore create(File file) {
        Future<InputStream> stream = null;
        if (file.getId().isSet() && file.getSize() > 0)
            stream = drive.downloadFileRangeAsync(
                    file.toDaFile(EnumSet.of(Field.DOWNLOAD_URL)), 0, file.getSize(), executor);
        return new FullAccessOpenedFile(tempStoreFactory.create(), stream, readerFactory);
    }
}
