package jdbox.filetree.knownfiles;

import jdbox.models.File;
import jdbox.models.fileids.FileId;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * This class is not thread safe, all synchronization should be done externally.
 */
public class KnownFiles {

    private volatile KnownFile root;

    private final Map<FileId, KnownFile> entries = new HashMap<>();

    public KnownFile getRoot() {
        return root;
    }

    public void setRoot(FileId rootId) {
        if (root != null)
            entries.clear();
        root = new KnownFile(rootId, true, this);
        put(root);
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
