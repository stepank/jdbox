package jdbox.filetree.knownfiles;

import com.google.common.collect.ImmutableMap;
import jdbox.models.File;
import jdbox.models.fileids.FileId;

import java.util.*;

/**
 * This class is not thread safe, all synchronization should be done externally.
 */
public class KnownFile {

    private static ImmutableMap<String, String> extensions = ImmutableMap.copyOf(new HashMap<String, String>() {{
        put("application/pdf", "pdf");
        put("image/png", "png");
        put("image/git", "gif");
        put("image/jpeg", "jpg");
        put("application/vnd.google-apps.drawing", "desktop");
        put("application/vnd.google-apps.document", "desktop");
        put("application/vnd.google-apps.presentation", "desktop");
        put("application/vnd.google-apps.spreadsheet", "desktop");
    }});

    private File self;

    private Map<String, KnownFile> children;
    private final HashMap<KnownFile, String> parents = new HashMap<>();

    private final KnownFiles knownFiles;

    KnownFile(FileId fileId, boolean isDirectory, KnownFiles knownFiles) {

        self = new File(fileId);
        self.setIsDirectory(isDirectory);

        this.knownFiles = knownFiles;
    }

    KnownFile(FileId fileId, String name, boolean isDirectory, Date createdDate, KnownFiles knownFiles) {

        self = new File(fileId);
        self.setName(name);
        self.setIsDirectory(isDirectory);
        self.setCreatedDate(createdDate);
        self.setParentIds(new HashSet<FileId>());

        this.knownFiles = knownFiles;
    }

    KnownFile(File file, KnownFiles knownFiles) {
        self = file.clone();
        this.knownFiles = knownFiles;
    }

    public FileId getId() {
        return self.getId();
    }

    public String getName() {
        return self.getName();
    }

    public boolean isDirectory() {
        return self.isDirectory();
    }

    public Set<KnownFile> getParents() {
        return Collections.unmodifiableSet(parents.keySet());
    }

    public void setUploaded(String id, String downloadUrl) {
        self.getId().set(id);
        self.setDownloadUrl(downloadUrl);
    }

    public void setDates(Date modifiedDate, Date accessedDate) {
        self.setModifiedDate(modifiedDate);
        self.setAccessedDate(accessedDate);
    }

    public void setSize(long size) {
        self.setSize(size);
    }

    public void setTracked() {
        children = new HashMap<>();
    }

    public Map<String, KnownFile> getChildrenOrNull() {
        if (children == null)
            return null;
        return Collections.unmodifiableMap(children);
    }

    public void tryAddChild(KnownFile child) {

        child.self.getParentIds().add(getId());

        if (children == null)
            return;

        String extension = extensions.get(child.self.getMimeType());
        String nameWoExtension = child.self.getName();

        if (extension != null && nameWoExtension.endsWith(extension))
            nameWoExtension = nameWoExtension.substring(0, nameWoExtension.length() - extension.length() - 1);

        int index = 1;
        String knownByName;
        KnownFile existing;

        do {

            knownByName = nameWoExtension + (index == 1 ? "" : (" " + index));

            if (extension != null)
                knownByName += "." + extension;

            existing = children.get(knownByName);

            if (existing == child)
                return;

            index += 1;

        } while (existing != null);

        children.put(knownByName, child);
        child.parents.put(this, knownByName);

        KnownFile previous = knownFiles.put(child);
        if (previous != null && previous != child)
            throw new IllegalStateException("two different KnownFile's with the same id must not exist");
    }

    public void tryRemoveChild(KnownFile child) {
        tryRemoveChild(child, true);
    }

    public void rename(String name) {

        if (getName().equals(name))
            return;

        Set<KnownFile> parents = new HashSet<>(this.parents.keySet());

        for (KnownFile parent : parents)
            parent.tryRemoveChild(this, false);

        self.setName(name);

        for (KnownFile parent : parents)
            parent.tryAddChild(this);
    }

    public void update(File file) {
        EnumSet<File.Field> fields = EnumSet.allOf(File.Field.class);
        fields.remove(File.Field.NAME);
        fields.remove(File.Field.PARENT_IDS);
        self.update(file, fields);
    }

    public File toFile() {
        return self.clone();
    }

    public File toFile(EnumSet<File.Field> fields) {
        return self.clone(fields);
    }

    private void tryRemoveChild(KnownFile child, boolean cleanUp) {

        child.self.getParentIds().remove(getId());

        if (children == null)
            return;

        children.remove(child.parents.get(this));
        child.parents.remove(this);

        if (cleanUp)
            child.tryRemove();
    }

    private void tryRemove() {

        if (parents.size() != 0)
            return;

        knownFiles.remove(this);

        Map<String, KnownFile> children = getChildrenOrNull();
        if (children != null) {
            for (KnownFile child : new LinkedList<>(children.values()))
                tryRemoveChild(child);
        }
    }

    @Override
    public String toString() {
        return "KnownFile{" +
                "id=" + getId() +
                ", name='" + getName() + '\'' +
                ", isDirectory=" + isDirectory() +
                ", size=" + self.getSize() +
                '}';
    }
}
