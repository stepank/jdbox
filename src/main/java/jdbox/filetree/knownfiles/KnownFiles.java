package jdbox.filetree.knownfiles;

import jdbox.filetree.FileId;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

public class KnownFiles {

    private KnownFile root;

    private final Map<FileId, KnownFile> entries = new HashMap<>();

    public KnownFile getRoot() {
        return root;
    }

    public void setRoot(KnownFile file) {
        if (root != null) {
            remove(root);
        }
        root = file;
        put(root);
    }

    public KnownFile get(FileId id) {
        return entries.get(id);
    }

    public void put(KnownFile file) {
        entries.put(file.self.getId(), file);
    }

    public void remove(KnownFile file) {

        for (KnownFile parent : file.getParents()) {
            parent.tryRemoveChild(file);
        }

        Map<String, KnownFile> children = file.getChildrenOrNull();
        if (children != null) {
            for (KnownFile child : new LinkedList<>(children.values())) {
                tryRemoveChild(file, child);
                removeIfHasNoParents(child);
            }
        }

        if (file.self.getId().isSet())
            entries.remove(file.self.getId());
    }

    public boolean removeIfHasNoParents(KnownFile child) {
        if (child.getParents().size() != 0)
            return false;
        remove(child);
        return true;
    }

    public boolean tryAddChild(KnownFile parent, KnownFile child) {
        if (!parent.tryAddChild(child))
            return false;
        child.addParent(parent);
        return true;
    }

    public void tryRemoveChild(KnownFile parent, KnownFile child) {
        if (parent.tryRemoveChild(child))
            child.removeParent(parent);
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
}
