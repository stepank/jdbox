package jdbox.content;

import com.google.inject.Inject;
import jdbox.content.bytestores.ByteStore;
import jdbox.content.filetypes.*;
import jdbox.content.localstorage.LocalStorage;
import jdbox.models.File;
import jdbox.uploader.Uploader;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

public class OpenedFiles implements OpenedFilesManager {

    public static Config defaultConfig = new Config();

    public enum OpenMode {
        READ_ONLY,
        WRITE_ONLY,
        READ_WRITE
    }

    private final OpenedFileFactory nonDownloadableOpenedFileFactory;
    private final UploadStatusOpenedFileFactory uploadStatusOpenedFileFactory;
    private final OpenedFileFactory fullAccessOpenedFileFactory;
    private final OpenedFileFactory rollingReadOpenedFileFactory;

    private final LocalStorage localStorage;

    private volatile Config config;

    private final Map<Long, FileHandlerRemovingProxyByteStore> fileHandlers = new HashMap<>();
    private long currentFileHandler = 1;

    @Inject
    public OpenedFiles(
            NonDownloadableOpenedFileFactory nonDownloadableOpenedFileFactory,
            FullAccessOpenedFileFactory fullAccessOpenedFileFactory,
            RollingReadOpenedFileFactory rollingReadOpenedFileFactory,
            UploadStatusOpenedFileFactory uploadStatusOpenedFileFactory,
            LocalStorage localStorage, Config config) {

        this.nonDownloadableOpenedFileFactory = nonDownloadableOpenedFileFactory;
        this.fullAccessOpenedFileFactory = new LocalStorageOpenedFileFactory(fullAccessOpenedFileFactory);
        this.rollingReadOpenedFileFactory = rollingReadOpenedFileFactory;
        this.uploadStatusOpenedFileFactory = uploadStatusOpenedFileFactory;

        this.localStorage = localStorage;
        this.config = config;
    }

    public void setConfig(Config config) {
        this.config = config;
    }

    public int getLocalFilesCount() {
        return localStorage.getFilesCount();
    }

    @Override
    public synchronized int getOpenedFilesCount() {
        return fileHandlers.size();
    }

    @Override
    public void reset() {
        localStorage.reset();
    }

    public synchronized FileHandlerRemovingProxyByteStore open(File file, OpenMode openMode) throws IOException {

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

    private OpenedFileFactory getOpenedFileFactory(File file, OpenMode openMode) {
        if (file.getId().isSet() && file.getId().get().equals(Uploader.uploadFailureNotificationFileId))
            return uploadStatusOpenedFileFactory;
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

    class LocalStorageOpenedFileFactory implements OpenedFileFactory {

        private final OpenedFileFactory factory;

        LocalStorageOpenedFileFactory(OpenedFileFactory factory) {
            this.factory = factory;
        }

        @Override
        public long getSize(File file) {
            return factory.getSize(file);
        }

        @Override
        public ByteStore create(File file) throws IOException {
            return localStorage.putContent(file, factory.create(file));
        }
    }
}
