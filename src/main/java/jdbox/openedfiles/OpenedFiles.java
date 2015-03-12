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
    private final OpenedFileFactory rangeMappedOpenedFileFactory;
    private final OpenedFileFactory rollingReadOpenedFileFactory;

    private final Map<Long, OpenedFileInfo> fileHandlers = new HashMap<>();
    private long currentFileHandler = 1;

    @Inject
    public OpenedFiles(
            NonDownloadableOpenedFileFactory nonDownloadableOpenedFileFactory,
            RangeMappedOpenedFileFactory rangeMappedOpenedFileFactory,
            RollingReadOpenedFileFactory rollingReadOpenedFileFactory) {
        this.nonDownloadableOpenedFileFactory = nonDownloadableOpenedFileFactory;
        this.rangeMappedOpenedFileFactory = rangeMappedOpenedFileFactory;
        this.rollingReadOpenedFileFactory = rollingReadOpenedFileFactory;
    }

    public int getFileHandlersCount() {
        return fileHandlers.size();
    }

    public synchronized long open(File file, OpenMode openMode) {

        currentFileHandler++;

        OpenedFileFactory openedFileFactory;

        if (!file.isReal() && openMode.equals(OpenMode.READ_ONLY))
            openedFileFactory = nonDownloadableOpenedFileFactory;
        else if (file.isReal() && !isLargeFile(file))
            openedFileFactory = rangeMappedOpenedFileFactory;
        else if (file.isReal() && isLargeFile(file))
            openedFileFactory = rollingReadOpenedFileFactory;
        else
            throw new UnsupportedOperationException();

        fileHandlers.put(currentFileHandler, new OpenedFileInfo(openedFileFactory.create(file), openedFileFactory));

        return currentFileHandler;
    }

    public synchronized void close(long fileHandler) throws Exception {
        OpenedFileInfo openedFileInfo = fileHandlers.remove(fileHandler);
        openedFileInfo.openedFileFactory.close(openedFileInfo.openedFile);
    }

    public synchronized OpenedFile get(long fileHandler) {
        return fileHandlers.get(fileHandler).openedFile;
    }

    private static boolean isLargeFile(File file) {
        return file.getSize() > 1024 * 1024;
    }

    private class OpenedFileInfo {

        OpenedFile openedFile;
        OpenedFileFactory openedFileFactory;

        public OpenedFileInfo(OpenedFile openedFile, OpenedFileFactory openedFileFactory) {
            this.openedFile = openedFile;
            this.openedFileFactory = openedFileFactory;
        }
    }
}

interface OpenedFileFactory {

    public OpenedFile create(File file);

    public void close(OpenedFile openedFile) throws Exception;
}
