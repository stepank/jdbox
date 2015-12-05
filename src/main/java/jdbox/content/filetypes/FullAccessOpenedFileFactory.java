package jdbox.content.filetypes;

import com.google.inject.Inject;
import jdbox.content.PackagePrivate;
import jdbox.content.bytestores.ByteStore;
import jdbox.content.bytestores.InMemoryByteStoreFactory;
import jdbox.content.bytestores.StreamCachingByteSourceFactory;
import jdbox.driveadapter.DriveAdapter;
import jdbox.driveadapter.Field;
import jdbox.models.File;

import java.io.InputStream;
import java.util.EnumSet;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;

public class FullAccessOpenedFileFactory implements OpenedFileFactory {

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
