package jdbox.openedfiles;

import jdbox.models.File;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

class NonDownloadableOpenedFile implements ByteStore {

    private static String contentTemplate =
            "[Desktop Entry]\n" +
                    "Version=1.0\n" +
                    "Encoding=UTF-8\n" +
                    "Type=Link\n" +
                    "Name={name}\n" +
                    "URL={href}\n" +
                    "Icon=text-html\n";

    private final File file;

    NonDownloadableOpenedFile(File file) {
        this.file = file;
    }

    @Override
    public int read(ByteBuffer buffer, long offset, int count) throws IOException {
        String content = getContent(file);
        buffer.put(Arrays.copyOfRange(content.getBytes(), (int) offset, (int) (offset + count)));
        return (int) Math.min(count, content.length() - offset);
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

    public static String getContent(File file) {
        return contentTemplate
                .replace("{name}", file.getName())
                .replace("{href}", file.getAlternateLink());
    }
}

class NonDownloadableOpenedFileFactory implements ByteStoreFactory {

    @Override
    public NonDownloadableOpenedFile create(File file) {
        return new NonDownloadableOpenedFile(file);
    }
}
