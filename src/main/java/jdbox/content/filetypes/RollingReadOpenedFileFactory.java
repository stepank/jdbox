package jdbox.content.filetypes;

import com.google.inject.Inject;
import jdbox.content.PackagePrivate;
import jdbox.content.bytestores.StreamCachingByteSourceFactory;
import jdbox.driveadapter.DriveAdapter;
import jdbox.models.File;

import java.util.concurrent.Executor;

public class RollingReadOpenedFileFactory implements OpenedFileFactory {

    public static RollingReadOpenedFileFactory.Config defaultConfig = new RollingReadOpenedFileFactory.Config();

    private final DriveAdapter drive;
    private final StreamCachingByteSourceFactory readerFactory;
    private final Executor executor;

    private volatile RollingReadOpenedFileFactory.Config config;

    @Inject
    public RollingReadOpenedFileFactory(
            DriveAdapter drive, StreamCachingByteSourceFactory readerFactory,
            @PackagePrivate Executor executor, RollingReadOpenedFileFactory.Config config) {
        this.drive = drive;
        this.readerFactory = readerFactory;
        this.executor = executor;
        this.config = config;
    }

    public void setConfig(RollingReadOpenedFileFactory.Config config) {
        this.config = config;
    }

    @Override
    public long getSize(File file) {
        return file.getSize();
    }

    @Override
    public RollingReadOpenedFile create(File file) {
        return new RollingReadOpenedFile(file, drive, readerFactory, config.minPageSize, config.maxPageSize, executor);
    }

    public static class Config {

        public final int minPageSize;
        public final int maxPageSize;

        public Config() {
            minPageSize = 4 * 1024 * 1024;
            maxPageSize = 16 * 1024 * 1024;
        }

        public Config(int minPageSize, int maxPageSize) {
            this.minPageSize = minPageSize;
            this.maxPageSize = maxPageSize;
        }
    }
}
