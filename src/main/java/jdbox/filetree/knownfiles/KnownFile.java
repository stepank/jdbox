package jdbox.filetree.knownfiles;

import jdbox.filetree.File;

import java.util.*;

/**
 * This class is not thread safe, all synchronization should be done externally.
 */
public class KnownFile {

    public final File self;

    private Map<String, KnownFile> children;
    private final Set<KnownFile> parents = new HashSet<>();

    public KnownFile(File file) {
        this.self = file;
    }

    public void setTracked() {
        children = new HashMap<>();
    }

    public Map<String, KnownFile> getChildrenOrNull() {
        if (children == null)
            return null;
        return Collections.unmodifiableMap(children);
    }

    boolean tryAddChild(KnownFile file) {
        if (children == null)
            return false;
        addChild(file);
        return true;
    }

    void addChild(KnownFile child) {
        if (children == null)
            throw new NullPointerException("children");
        children.put(child.self.getName(), child);
    }

    boolean tryRemoveChild(KnownFile child) {
        if (children == null)
            return false;
        removeChild(child);
        return true;
    }

    void removeChild(KnownFile child) {
        if (children == null)
            throw new NullPointerException("children");
        children.remove(child.self.getName());
    }

    public Set<KnownFile> getParents() {
        return Collections.unmodifiableSet(parents);
    }

    void addParent(KnownFile parent) {
        parents.add(parent);
    }

    void removeParent(KnownFile parent) {
        parents.remove(parent);
    }
}
