package jdbox.content.filetypes;

import jdbox.models.File;

public class NonDownloadableOpenedFileFactory implements OpenedFileFactory {

    @Override
    public long getSize(File file) {
        return NonDownloadableOpenedFile.getContent(file).length();
    }

    @Override
    public NonDownloadableOpenedFile create(File file) {
        return new NonDownloadableOpenedFile(file);
    }
}
