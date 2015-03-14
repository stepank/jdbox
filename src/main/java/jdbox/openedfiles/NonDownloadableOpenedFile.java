package jdbox.openedfiles;

import jdbox.filetree.File;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class NonDownloadableOpenedFile implements OpenedFile {

    private final File file;

    NonDownloadableOpenedFile(File file) {
        this.file = file;
    }

    @Override
    public int read(ByteBuffer buffer, long offset, int count) throws Exception {
        String exportInfo = file.getExportInfo();
        buffer.put(Arrays.copyOfRange(exportInfo.getBytes(), (int) offset, (int) (offset + count)));
        return (int) Math.min(count, exportInfo.length() - offset);
    }

    @Override
    public int write(ByteBuffer buffer, long offset, int count) throws Exception {
        throw new UnsupportedOperationException("write is not supported");
    }

    @Override
    public void truncate(long offset) throws Exception {
        throw new UnsupportedOperationException("truncate is not supported");
    }
}

class NonDownloadableOpenedFileFactory implements OpenedFileFactory {

    @Override
    public NonDownloadableOpenedFile create(File file) {
        return new NonDownloadableOpenedFile(file);
    }

    @Override
    public void close(OpenedFile openedFile) {
    }
}
