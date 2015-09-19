package jdbox.uploader;

public class UploadFailureEvent {

    public final Uploader.UploadStatus uploadStatus;

    public UploadFailureEvent(Uploader.UploadStatus uploadStatus) {
        this.uploadStatus = uploadStatus;
    }
}
