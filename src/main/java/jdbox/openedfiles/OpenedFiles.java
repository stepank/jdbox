package jdbox.openedfiles;

import com.google.inject.Inject;
import jdbox.filetree.File;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

public class OpenedFiles {

    public static Config defaultConfig = new Config();

    public enum OpenMode {
        READ_ONLY,
        WRITE_ONLY,
        READ_WRITE
    }

    private final OpenedFileFactory nonDownloadableOpenedFileFactory;
    private final OpenedFileFactory fullAccessOpenedFileFactory;
    private final OpenedFileFactory rollingReadOpenedFileFactory;
    private final LocalStorage localStorage;

    private volatile Config config;

    private final Map<Long, OpenedFile> fileHandlers = new HashMap<>();
    private long currentFileHandler = 1;

    @Inject
    public OpenedFiles(
            NonDownloadableOpenedFileFactory nonDownloadableOpenedFileFactory,
            final FullAccessOpenedFileFactory fullAccessOpenedFileFactory,
            RollingReadOpenedFileFactory rollingReadOpenedFileFactory,
            final LocalStorage localStorage, Config config) {

        this.nonDownloadableOpenedFileFactory = nonDownloadableOpenedFileFactory;
        this.rollingReadOpenedFileFactory = rollingReadOpenedFileFactory;
        this.localStorage = localStorage;
        this.config = config;

        this.fullAccessOpenedFileFactory = new OpenedFileFactory() {
            @Override
            public ByteStore create(File file) {
                return localStorage.putContent(file, fullAccessOpenedFileFactory.create(file));
            }
        };
    }

    public void setConfig(Config config) {
        this.config = config;
    }

    public synchronized OpenedFile open(File file, OpenMode openMode) {

        currentFileHandler++;

        ByteStore openedFile = localStorage.getContent(file);
        if (openedFile == null)
            openedFile = getOpenedFileFactory(file, openMode).create(file);

        OpenedFile openedFileHandler = new OpenedFile(currentFileHandler, openedFile);

        fileHandlers.put(currentFileHandler, openedFileHandler);

        return openedFileHandler;
    }

    public synchronized OpenedFile get(long fileHandler) {
        return fileHandlers.get(fileHandler);
    }

    private OpenedFileFactory getOpenedFileFactory(File file, OpenMode openMode) {
        if (!file.isReal() && openMode.equals(OpenMode.READ_ONLY))
            return nonDownloadableOpenedFileFactory;
        if (file.isReal() && !isLargeFile(file))
            return fullAccessOpenedFileFactory;
        if (file.isReal() && isLargeFile(file))
            return rollingReadOpenedFileFactory;
        throw new UnsupportedOperationException();
    }

    private boolean isLargeFile(File file) {
        return file.getSize() > config.largeFileSize;
    }

    public static class Config {

        public final int largeFileSize;

        public Config() {
            largeFileSize = 1024 * 1024;
        }

        public Config(int largeFileSize) {
            this.largeFileSize = largeFileSize;
        }
    }

    public class OpenedFile implements ByteStore {

        public final long handler;
        private final ByteStore content;

        public OpenedFile(long handler, ByteStore content) {
            this.handler = handler;
            this.content = content;
        }

        @Override
        public int read(ByteBuffer buffer, long offset, int count) throws IOException {
            return content.read(buffer, offset, count);
        }

        @Override
        public int write(ByteBuffer buffer, long offset, int count) throws IOException {
            return content.write(buffer, offset, count);
        }

        @Override
        public void truncate(long offset) throws IOException {
            content.truncate(offset);
        }

        @Override
        public void close() throws IOException {
            synchronized (OpenedFiles.this) {
                fileHandlers.remove(handler);
            }
            content.close();
        }
    }
}

interface OpenedFileFactory {
    ByteStore create(File file);
}
