package jdbox.uploader;

import jdbox.models.fileids.FileId;

public class FileEtagUpdateEvent {

    public final FileId fileId;
    public final String etag;

    public FileEtagUpdateEvent(FileId fileId, String etag) {
        this.fileId = fileId;
        this.etag = etag;
    }
}
