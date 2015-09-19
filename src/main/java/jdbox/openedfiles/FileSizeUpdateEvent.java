package jdbox.openedfiles;

import jdbox.models.fileids.FileId;

public class FileSizeUpdateEvent {

    public final FileId fileId;
    public final long fileSize;

    public FileSizeUpdateEvent(FileId fileId, long fileSize) {
        this.fileId = fileId;
        this.fileSize = fileSize;
    }
}
