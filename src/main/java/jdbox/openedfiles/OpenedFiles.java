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

    private final OpenedFileFactory nonDownloadableOpenedFileFactory;
    private final OpenedFileFactory fullAccessOpenedFileFactory;
    private final OpenedFileFactory rollingReadOpenedFileFactory;

    private final LocalStorage localStorage;

    private volatile Config config;

    private final Map<Long, ProxyOpenedFile> fileHandlers = new HashMap<>();
    private long currentFileHandler = 1;

    @Inject
    public OpenedFiles(
            NonDownloadableOpenedFileFactory nonDownloadableOpenedFileFactory,
            final FullAccessOpenedFileFactory fullAccessOpenedFileFactory,
            RollingReadOpenedFileFactory rollingReadOpenedFileFactory,
            final LocalStorage localStorage, Config config) {

        this.nonDownloadableOpenedFileFactory = new ByteStoreFactoryWrapper(nonDownloadableOpenedFileFactory);
        this.fullAccessOpenedFileFactory = new LocalStorageOpenedFileFactory(fullAccessOpenedFileFactory);
        this.rollingReadOpenedFileFactory = new ByteStoreFactoryWrapper(rollingReadOpenedFileFactory);

        this.localStorage = localStorage;
        this.config = config;
    }

    public void setConfig(Config config) {
        this.config = config;
    }

    public synchronized ProxyOpenedFile open(File file, OpenMode openMode) {

        currentFileHandler++;

        OpenedFile openedFile = localStorage.getContent(file);
        if (openedFile == null)
            openedFile = getOpenedFileFactory(file, openMode).create(file);

        ProxyOpenedFile openedFileHandler = new ProxyOpenedFile(currentFileHandler, openedFile);

        fileHandlers.put(currentFileHandler, openedFileHandler);

        return openedFileHandler;
    }

    public synchronized ProxyOpenedFile get(long fileHandler) {
        return fileHandlers.get(fileHandler);
    }

    public Long getSize(File file) {
        if (file.isDirectory())
            return null;
        if (!isReal(file))
            return (long) (NonDownloadableOpenedFile.getContent(file).length());
        return localStorage.getSize(file);
    }

    public boolean isWritable(File file) {
        return isReal(file) && !isLargeFile(file);
    }

    private OpenedFileFactory getOpenedFileFactory(File file, OpenMode openMode) {
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

    public class ProxyOpenedFile extends OpenedFile {

        public final long handler;
        private final OpenedFile content;

        public ProxyOpenedFile(long handler, OpenedFile content) {
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
        public File release() throws IOException {
            synchronized (OpenedFiles.this) {
                fileHandlers.remove(handler);
            }
            return content.release();
        }
    }

    interface OpenedFileFactory {
        OpenedFile create(File file);
    }

    class ByteStoreFactoryWrapper implements OpenedFileFactory {

        private final ByteStoreFactory factory;

        ByteStoreFactoryWrapper(ByteStoreFactory factory) {
            this.factory = factory;
        }

        @Override
        public OpenedFile create(File file) {
            return new ByteStoreWrapper(factory.create(file));
        }

        public class ByteStoreWrapper extends OpenedFile {

            private final ByteStore origin;

            public ByteStoreWrapper(ByteStore origin) {
                this.origin = origin;
            }

            @Override
            public int read(ByteBuffer buffer, long offset, int count) throws IOException {
                return origin.read(buffer, offset, count);
            }

            @Override
            public int write(ByteBuffer buffer, long offset, int count) throws IOException {
                return origin.write(buffer, offset, count);
            }

            @Override
            public void truncate(long offset) throws IOException {
                origin.truncate(offset);
            }

            @Override
            public File release() throws IOException {
                origin.close();
                return null;
            }
        }
    }

    class LocalStorageOpenedFileFactory implements OpenedFileFactory {

        private final ByteStoreFactory factory;

        LocalStorageOpenedFileFactory(ByteStoreFactory factory) {
            this.factory = factory;
        }

        @Override
        public OpenedFile create(File file) {
            return localStorage.putContent(file, factory.create(file));
        }
    }
}
