package jdbox.openedfiles;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.inject.Inject;
import jdbox.DriveAdapter;
import jdbox.Uploader;
import jdbox.filetree.File;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

public class OpenedFiles {

    public enum OpenMode {
        READ_ONLY,
        WRITE_ONLY,
        READ_WRITE
    }

    private static final Logger logger = LoggerFactory.getLogger(RangeMappedOpenedFile.class);

    private final DriveAdapter drive;
    private final Uploader uploader;
    private final ScheduledExecutorService executor;

    private final Map<Long, OpenedFile> fileHandlers = new HashMap<>();
    private final Map<File, SharedOpenedFile> sharedFiles = new HashMap<>();
    private long currentFileHandler = 1;

    @Inject
    public OpenedFiles(DriveAdapter drive, Uploader uploader, ScheduledExecutorService executor) {
        this.drive = drive;
        this.uploader = uploader;
        this.executor = executor;
    }

    public int getFileHandlersCount() {
        return sharedFiles.size();
    }

    public int getSharedFilesCount() {
        return sharedFiles.size();
    }

    public synchronized long open(File file, OpenMode openMode) {

        currentFileHandler++;

        SharedOpenedFile sharedFile = sharedFiles.get(file);

        if (sharedFile != null) {
            fileHandlers.put(currentFileHandler, sharedFile.file);
            sharedFile.refCount++;
        } else if (!file.isUploaded() || file.isDownloadable() && !isLargeFile(file)) {
            sharedFile = new SharedOpenedFile(RangeMappedOpenedFile.create(file, drive, uploader, executor));
            sharedFiles.put(file, sharedFile);
            fileHandlers.put(currentFileHandler, sharedFile.file);
        } else if (openMode.equals(OpenMode.READ_ONLY)) {
            if (!file.isDownloadable())
                fileHandlers.put(currentFileHandler, new NonDownloadableOpenedFile(file));
            else
                fileHandlers.put(currentFileHandler, new RollingReadOpenedFile(file, drive, executor));
        } else {
            // large or non-downloadable files that one tries to open for writing
            throw new UnsupportedOperationException();
        }

        return currentFileHandler;
    }

    public synchronized void close(long fileHandler) throws Exception {

        OpenedFile openedFile = fileHandlers.remove(fileHandler);
        final SharedOpenedFile sharedFile = sharedFiles.get(openedFile.getOrigin());

        if (sharedFile == null)
            openedFile.close();
        else {

            assert sharedFile.refCount > 0;

            ListenableFuture future = sharedFile.file.flush();

            if (future == null) {
                sharedFile.refCount--;
                if (sharedFile.refCount == 0)
                    sharedFiles.remove(sharedFile.file.getOrigin()).file.close();
            } else {
                future.addListener(new Runnable() {
                    @Override
                    public void run() {
                        synchronized (OpenedFiles.this) {

                            sharedFile.refCount--;
                            if (sharedFile.refCount > 0)
                                return;

                            SharedOpenedFile sharedOpenedFile;
                            try {
                                sharedOpenedFile = sharedFiles.remove(sharedFile.file.getOrigin());
                                sharedOpenedFile.file.close();
                            } catch (Exception e) {
                                logger.error("an error occured while closing a shared file", e);
                            }
                        }
                    }
                }, executor);
            }
        }
    }

    public synchronized OpenedFile get(long fileHandler) {
        return fileHandlers.get(fileHandler);
    }

    private static boolean isLargeFile(File file) {
        return file.getSize() > 16 * 1024 * 1024;
    }

    private class SharedOpenedFile {

        public final RangeMappedOpenedFile file;
        public int refCount = 1;

        private SharedOpenedFile(RangeMappedOpenedFile file) {
            this.file = file;
        }
    }
}
