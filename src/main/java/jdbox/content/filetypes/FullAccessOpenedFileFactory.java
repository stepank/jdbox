package jdbox.content.filetypes;

import com.google.inject.Inject;
import jdbox.content.ByteStreamReader;
import jdbox.content.PackagePrivate;
import jdbox.content.bytestores.ByteStore;
import jdbox.content.bytestores.InMemoryByteStoreFactory;
import jdbox.driveadapter.DriveAdapter;
import jdbox.driveadapter.Field;
import jdbox.models.File;

import java.io.IOException;
import java.io.InputStream;
import java.util.EnumSet;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;

public class FullAccessOpenedFileFactory implements OpenedFileFactory {

    public static Config defaultConfig = new Config();

    private final DriveAdapter drive;
    private final InMemoryByteStoreFactory tempStoreFactory;
    private final Executor executor;

    private volatile Config config;

    @Inject
    FullAccessOpenedFileFactory(
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
    public ByteStore create(File file) throws IOException {
        ByteStore result = tempStoreFactory.create();
        if (file.getId().isSet() && file.getSize() > 0) {
            Future<InputStream> stream = drive.downloadFileRangeAsync(
                    file.toDaFile(EnumSet.of(Field.DOWNLOAD_URL)), 0, 0, executor);
            ByteStreamReader bsr = new ByteStreamReader(stream, result, config.bufferSize);
            bsr.ensureStreamIsRead(0);
        }
        return result;
    }

    public static class Config {

        public final int bufferSize;

        public Config() {
            bufferSize = 16 * 1024;
        }

        public Config(int bufferSize) {
            this.bufferSize = bufferSize;
        }
    }
}
