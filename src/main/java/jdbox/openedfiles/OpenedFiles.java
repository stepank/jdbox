package jdbox.openedfiles;

import com.google.inject.Inject;
import jdbox.filetree.File;

import java.util.HashMap;
import java.util.Map;

public class OpenedFiles {

    public enum OpenMode {
        READ_ONLY,
        WRITE_ONLY,
        READ_WRITE
    }

    private final OpenedFileFactory nonDownloadableOpenedFileFactory;
    private final OpenedFileFactory fullAccessOpenedFileFactory;
    private final OpenedFileFactory rollingReadOpenedFileFactory;

    private final Map<Long, ByteStore> fileHandlers = new HashMap<>();
    private long currentFileHandler = 1;

    @Inject
    public OpenedFiles(
            NonDownloadableOpenedFileFactory nonDownloadableOpenedFileFactory,
            FullAccessOpenedFileFactory fullAccessOpenedFileFactory,
            RollingReadOpenedFileFactory rollingReadOpenedFileFactory) {
        this.nonDownloadableOpenedFileFactory = nonDownloadableOpenedFileFactory;
        this.fullAccessOpenedFileFactory = fullAccessOpenedFileFactory;
        this.rollingReadOpenedFileFactory = rollingReadOpenedFileFactory;
    }

    public synchronized long open(File file, OpenMode openMode) {

        currentFileHandler++;

        OpenedFileFactory openedFileFactory;

        if (!file.isReal() && openMode.equals(OpenMode.READ_ONLY))
            openedFileFactory = nonDownloadableOpenedFileFactory;
        else if (file.isReal() && !isLargeFile(file))
            openedFileFactory = fullAccessOpenedFileFactory;
        else if (file.isReal() && isLargeFile(file))
            openedFileFactory = rollingReadOpenedFileFactory;
        else
            throw new UnsupportedOperationException();

        fileHandlers.put(currentFileHandler, openedFileFactory.create(file));

        return currentFileHandler;
    }

    public synchronized void close(long fileHandler) throws Exception {
        fileHandlers.remove(fileHandler).close();
    }

    public synchronized ByteStore get(long fileHandler) {
        return fileHandlers.get(fileHandler);
    }

    private static boolean isLargeFile(File file) {
        return file.getSize() > 1024 * 1024;
    }
}

interface OpenedFileFactory {
    ByteStore create(File file);
}
