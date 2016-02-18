package jdbox.driveadapter;

public class Change {

    public final String fileId;
    public final File file;
    public final boolean isDeleted;

    Change(com.google.api.services.drive.model.Change change) {
        fileId = change.getFileId();
        file = change.getFile() != null ? new File(change.getFile()) : null;
        isDeleted = change.getDeleted();
    }
}
