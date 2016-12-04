package jdbox.localstate.knownfiles;

import jdbox.models.File;
import jdbox.models.fileids.FileId;
import jdbox.models.fileids.FileIdStore;
import jdbox.uploader.Uploader;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * This class is not thread safe, all synchronization should be done externally.
 */
public class KnownFiles {

    public static final String uploadFailureNotificationFileName = "READ ME - UPLOAD IS BROKEN.txt";

    private KnownFile root;
    private final Map<String, KnownFile> entries = new HashMap<>();
    private long largestChangeId = 0;
    private KnownFile uploadFailureNotificationFile;

    public KnownFile getRoot() {
        return root;
    }

    public void setRoot(String rootId) {

        if (root != null)
            throw new IllegalStateException("root is already set");

        root = new KnownFile(fileIdStore.get(rootId), "{root}", true, null, this);
        put(root);
    }

    public void reset() {

        if (root == null)
            return;

        String rootId = root.getId().get();

        root = null;
        entries.clear();
        uploadFailureNotificationFile = null;

        setRoot(rootId);
    }

    public long getLargestChangeId() {
        return largestChangeId;
    }

    public void setLargestChangeId(long largestChangeId) {
        this.largestChangeId = largestChangeId;
    }

    public KnownFile getUploadFailureNotificationFile() {
        return uploadFailureNotificationFile;
    }

    public void createOrUpdateUploadFailureNotificationFile(Date date) {

        if (uploadFailureNotificationFile != null) {

            uploadFailureNotificationFile.setDates(date, date);

        } else {

            uploadFailureNotificationFile = create(
                    fileIdStore.get(Uploader.uploadFailureNotificationFileId),
                    uploadFailureNotificationFileName, false, date);

            uploadFailureNotificationFile.setDates(date, date);

            root.tryAddChild(uploadFailureNotificationFile);
        }
    }

    public KnownFile create(FileId fileId, String name, boolean isDirectory, Date createdDate) {
        return new KnownFile(fileId, name, isDirectory, createdDate, this);
    }

    public KnownFile create(File file) {
        return new KnownFile(file, this);
    }

    public KnownFile get(FileId id) {
        return entries.get(id);
    }

    public int getFileCount() {
        return entries.size();
    }

    public int getTrackedDirCount() {
        return getTrackedDirCount(root);
    }

    private int getTrackedDirCount(KnownFile file) {

        Map<String, KnownFile> children = file.getChildrenOrNull();

        if (children == null)
            return 0;

        int result = 1;

        for (KnownFile child : children.values()) {
            result += getTrackedDirCount(child);
        }

        return result;
    }

    KnownFile put(KnownFile file) {
        return entries.put(file.getId(), file);
    }

    void remove(KnownFile file) {
        entries.remove(file.getId());
    }
}
