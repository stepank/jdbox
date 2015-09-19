package jdbox.openedfiles;

import com.google.inject.Inject;
import jdbox.models.File;

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

    private final ByteStoreFactory nonDownloadableOpenedFileFactory;
    private final ByteStoreFactory fullAccessOpenedFileFactory;
    private final ByteStoreFactory rollingReadOpenedFileFactory;

    private final LocalStorage localStorage;

    private volatile Config config;

    private final Map<Long, FileHandlerRemovingProxyByteStore> fileHandlers = new HashMap<>();
    private long currentFileHandler = 1;

    @Inject
    public OpenedFiles(
            NonDownloadableOpenedFileFactory nonDownloadableOpenedFileFactory,
            FullAccessOpenedFileFactory fullAccessOpenedFileFactory,
            RollingReadOpenedFileFactory rollingReadOpenedFileFactory,
            LocalStorage localStorage, Config config) {

        this.nonDownloadableOpenedFileFactory = nonDownloadableOpenedFileFactory;
        this.fullAccessOpenedFileFactory = new LocalStorageOpenedFileFactory(fullAccessOpenedFileFactory);
        this.rollingReadOpenedFileFactory = rollingReadOpenedFileFactory;

        this.localStorage = localStorage;
        this.config = config;
    }

    public void setConfig(Config config) {
        this.config = config;
    }

    public synchronized FileHandlerRemovingProxyByteStore open(File file, OpenMode openMode) {

        currentFileHandler++;

        ByteStore openedFile = localStorage.getContent(file);
        if (openedFile == null)
            openedFile = getOpenedFileFactory(file, openMode).create(file);

        FileHandlerRemovingProxyByteStore fileHandlerRemovingProxyByteStore =
                new FileHandlerRemovingProxyByteStore(currentFileHandler, openedFile);

        fileHandlers.put(currentFileHandler, fileHandlerRemovingProxyByteStore);

        return fileHandlerRemovingProxyByteStore;
    }

    public synchronized FileHandlerRemovingProxyByteStore get(long fileHandler) {
        return fileHandlers.get(fileHandler);
    }

    public long getSize(File file) {

        if (file.isDirectory())
            return 0;

        Long size = localStorage.getSize(file);
        if (size != null)
            return size;

        return getOpenedFileFactory(file, OpenMode.READ_ONLY).getSize(file);
    }

    public boolean isWritable(File file) {
        return isReal(file) && !isLargeFile(file);
    }

    private ByteStoreFactory getOpenedFileFactory(File file, OpenMode openMode) {
        if (!isReal(file) && openMode.equals(OpenMode.READ_ONLY))
            return nonDownloadableOpenedFileFactory;
        if (isWritable(file))
            return fullAccessOpenedFileFactory;
        if (isReal(file))
            return rollingReadOpenedFileFactory;
        throw new UnsupportedOperationException();
    }

    private boolean isReal(File file) {
        return !file.getId().isSet() || file.getDownloadUrl() != null && file.getDownloadUrl().length() != 0;
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

    public class FileHandlerRemovingProxyByteStore implements ByteStore {

        public final long handler;
        private final ByteStore content;

        public FileHandlerRemovingProxyByteStore(long handler, ByteStore content) {
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

    class LocalStorageOpenedFileFactory implements ByteStoreFactory {

        private final ByteStoreFactory factory;

        LocalStorageOpenedFileFactory(ByteStoreFactory factory) {
            this.factory = factory;
        }

        @Override
        public long getSize(File file) {
            return factory.getSize(file);
        }

        @Override
        public ByteStore create(File file) {
            return localStorage.putContent(file, factory.create(file));
        }
    }
}
