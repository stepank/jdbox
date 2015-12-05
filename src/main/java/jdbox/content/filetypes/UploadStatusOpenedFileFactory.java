package jdbox.content.filetypes;

import com.google.inject.Inject;
import jdbox.content.bytestores.ByteStore;
import jdbox.models.File;
import jdbox.uploader.Uploader;

public class UploadStatusOpenedFileFactory implements OpenedFileFactory {

    private final Uploader uploader;

    @Inject
    UploadStatusOpenedFileFactory(Uploader uploader) {
        this.uploader = uploader;
    }

    @Override
    public long getSize(File file) {
        return UploadStatusOpenedFile.getContent(uploader).length();
    }

    @Override
    public ByteStore create(File file) {
        return new UploadStatusOpenedFile(uploader);
    }
}
