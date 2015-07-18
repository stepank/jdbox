package jdbox.openedfiles;

import jdbox.models.fileids.FileId;

public class UpdateFileSizeEvent {

    public final FileId fileId;
    public final long fileSize;

    public UpdateFileSizeEvent(FileId fileId, long fileSize) {
        this.fileId = fileId;
        this.fileSize = fileSize;
    }
}
