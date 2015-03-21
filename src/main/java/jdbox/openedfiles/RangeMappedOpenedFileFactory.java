package jdbox.openedfiles;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.SettableFuture;
import com.google.inject.Inject;
import jdbox.DriveAdapter;
import jdbox.Uploader;
import jdbox.filetree.File;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class RangeMappedOpenedFileFactory implements OpenedFileFactory {

    public static Config defaultConfig = new Config();

    private static final Logger logger = LoggerFactory.getLogger(RangeMappedOpenedFileFactory.class);

    private final DriveAdapter drive;
    private final Uploader uploader;
    private final ListeningExecutorService executor;

    private final Map<File, SharedOpenedFile> sharedFiles = new HashMap<>();

    private volatile Config config;

    @Inject
    RangeMappedOpenedFileFactory(
            DriveAdapter drive, Uploader uploader, ListeningExecutorService executor, Config config) {
        this.drive = drive;
        this.uploader = uploader;
        this.executor = executor;
        this.config = config;
    }

    public synchronized int getSharedFilesCount() {
        return sharedFiles.size();
    }

    public void setConfig(Config config) {
        this.config = config;
    }

    @Override
    public synchronized RangeMappedOpenedFile create(final File file) {

        SharedOpenedFile sharedFile = sharedFiles.get(file);

        if (sharedFile != null) {
            sharedFile.refCount++;
            return sharedFile.file;
        }

        sharedFile = new SharedOpenedFile(create(file, 0, file.getSize()));
        sharedFiles.put(file, sharedFile);

        return sharedFile.file;
    }

    public RangeMappedOpenedFile create(final File file, final long offset, final long length) {

        final SettableFuture<InputStream> streamFuture;

        if (!file.isUploaded())
            streamFuture = null;
        else {

            logger.debug("requesting a stream of {}, offset {}, length {}", file, offset, length);

            streamFuture = SettableFuture.create();
            final Date start = new Date();
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        InputStream stream = drive.downloadFileRange(file, offset, length);
                        logger.debug(
                                "got a stream of {}, offset {}, length {}, exec time {} ms",
                                file, offset, length, new Date().getTime() - start.getTime());
                        streamFuture.set(stream);
                    } catch (Exception e) {
                        streamFuture.setException(e);
                    }
                }
            });
        }

        return new RangeMappedOpenedFile(file, drive, uploader, streamFuture, offset, length, config.bufferSize);
    }

    @Override
    public synchronized void close(OpenedFile openedFile) throws Exception {

        assert openedFile instanceof RangeMappedOpenedFile;

        final SharedOpenedFile sharedFile = sharedFiles.get(((RangeMappedOpenedFile) openedFile).getOrigin());

        assert sharedFile.refCount > 0;

        executor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    sharedFile.file.flush().addListener(new Runnable() {
                        @Override
                        public void run() {
                            synchronized (RangeMappedOpenedFileFactory.this) {

                                sharedFile.refCount--;
                                if (sharedFile.refCount > 0)
                                    return;

                                try {
                                    sharedFiles.remove(sharedFile.file.getOrigin()).file.close();
                                } catch (Exception e) {
                                    logger.error("an error occured while closing a shared file", e);
                                }
                            }
                        }
                    }, executor);
                } catch (Exception e) {
                    logger.error("an error occured while flushing a shared file", e);
                }
            }
        });
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

    private class SharedOpenedFile {

        public final RangeMappedOpenedFile file;
        public int refCount = 1;

        private SharedOpenedFile(RangeMappedOpenedFile file) {
            this.file = file;
        }
    }
}
