package jdbox.content.filetypes;

import com.google.inject.Inject;
import jdbox.content.ByteStreamReader;
import jdbox.content.PackagePrivate;
import jdbox.content.bytestores.ByteSource;
import jdbox.content.bytestores.ByteStore;
import jdbox.content.bytestores.InMemoryByteStoreFactory;
import jdbox.driveadapter.DriveAdapter;
import jdbox.driveadapter.Field;
import jdbox.models.File;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.EnumSet;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;

public class RollingReadOpenedFileFactory implements OpenedFileFactory {

    public static Config defaultConfig = new Config();

    private final DriveAdapter drive;
    private final InMemoryByteStoreFactory tempStoreFactory;
    private final Executor executor;

    private volatile Config config;

    @Inject
    public RollingReadOpenedFileFactory(
            DriveAdapter drive, InMemoryByteStoreFactory tempStoreFactory,
            @PackagePrivate Executor executor, Config config) {
        this.drive = drive;
        this.tempStoreFactory = tempStoreFactory;
        this.executor = executor;
        this.config = config;
    }

    public void setConfig(Config config) {
        this.config = config;
    }

    @Override
    public long getSize(File file) {
        return file.getSize();
    }

    @Override
    public RollingReadOpenedFile create(final File file) {
        return new RollingReadOpenedFile(
                file.getSize(), config.minPageSize, config.maxPageSize,
                new RollingReadOpenedFile.ReaderFactory() {
                    @Override
                    public ByteSource create(long offset, int length) {
                        return new StreamCachingByteSource(
                                drive.downloadFileRangeAsync(
                                        file.toDaFile(EnumSet.of(Field.DOWNLOAD_URL)), offset, length, executor),
                                tempStoreFactory.create(), config.readerBufferSize);
                    }
                });
    }

    public static class Config {

        public final int readerBufferSize;
        public final int minPageSize;
        public final int maxPageSize;

        public Config() {
            readerBufferSize = 16 * 1024;
            minPageSize = 4 * 1024 * 1024;
            maxPageSize = 16 * 1024 * 1024;
        }

        public Config(int readerBufferSize, int minPageSize, int maxPageSize) {
            this.readerBufferSize = readerBufferSize;
            this.minPageSize = minPageSize;
            this.maxPageSize = maxPageSize;
        }
    }

    /**
     * This class is not thread safe, all synchronization should be done externally.
     */
    private class StreamCachingByteSource implements ByteSource {

        private ByteStreamReader reader;
        private ByteStore destination;

        StreamCachingByteSource(Future<InputStream> source, ByteStore destination, int bufferSize) {
            this.reader = new ByteStreamReader(source, destination, bufferSize);
            this.destination = destination;
        }

        @Override
        public int read(ByteBuffer buffer, long offset, int count) throws IOException {

            if (destination == null)
                throw new IllegalStateException("read on a closed ByteSource");

            reader.ensureStreamIsRead(offset + count);

            return destination.read(buffer, offset, count);
        }

        @Override
        public void close() throws IOException {

            if (destination == null)
                return;

            destination = null;

            reader.close();
        }
    }
}
