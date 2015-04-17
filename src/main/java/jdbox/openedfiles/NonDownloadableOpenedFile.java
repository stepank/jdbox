package jdbox.openedfiles;

import jdbox.filetree.File;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class NonDownloadableOpenedFile implements ByteStore {

    private final File file;

    NonDownloadableOpenedFile(File file) {
        this.file = file;
    }

    @Override
    public int read(ByteBuffer buffer, long offset, int count) throws IOException {
        String exportInfo = file.getExportInfo();
        buffer.put(Arrays.copyOfRange(exportInfo.getBytes(), (int) offset, (int) (offset + count)));
        return (int) Math.min(count, exportInfo.length() - offset);
    }

    @Override
    public void close() throws IOException {
    }

    @Override
    public int write(ByteBuffer buffer, long offset, int count) throws IOException {
        throw new UnsupportedOperationException("write is not supported");
    }

    @Override
    public void truncate(long offset) throws IOException {
        throw new UnsupportedOperationException("truncate is not supported");
    }
}

class NonDownloadableOpenedFileFactory implements OpenedFileFactory {

    @Override
    public NonDownloadableOpenedFile create(File file) {
        return new NonDownloadableOpenedFile(file);
    }
}
