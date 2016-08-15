package jdbox.localstate.knownfiles;

import com.google.common.collect.ImmutableMap;
import jdbox.datapersist.ChangeSet;
import jdbox.models.File;
import jdbox.models.fileids.FileId;

import java.util.*;

/**
 * This class is not thread safe, all synchronization should be done externally.
 * This class wraps j.m.File by making a copy of it or by creating a new instance and must not allow this new instance
 * to leak anywhere.
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

    public boolean isTracked() {
        return children != null;
    }

    public Map<String, KnownFile> getChildrenOrNull() {
        if (!isTracked())
            return null;
        return Collections.unmodifiableMap(children);
    }

    public void setUploaded(
            ChangeSet changeSet, String id, String mimeType, String downloadUrl, String alternateLink) {
        self.getId().set(id);
        self.setMimeType(mimeType);
        self.setDownloadUrl(downloadUrl);
        self.setAlternateLink(alternateLink);
        save(changeSet);
    }

    public void setDates(ChangeSet changeSet, Date modifiedDate, Date accessedDate) {
        self.setModifiedDate(modifiedDate);
        self.setAccessedDate(accessedDate);
        save(changeSet);
    }

    public void setContentProperties(ChangeSet changeSet, long size, String md5Sum) {
        self.setSize(size);
        self.setMd5Sum(md5Sum);
        save(changeSet);
    }

    public void setEtag(ChangeSet changeSet, String etag) {
        self.setEtag(etag);
        save(changeSet);
    }

    public void setTracked(ChangeSet changeSet) {
        setTracked();
        save(changeSet);
    }

    public void update(ChangeSet changeSet, File file) {
        self.update(file);
        save(changeSet);
    }

    public void tryAddChild(ChangeSet changeSet, KnownFile child) {

        child.self.getParentIds().add(getId());
        save(changeSet, child);

        if (!isTracked())
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

    public void tryRemoveChild(ChangeSet changeSet, KnownFile child) {
        tryDetachChild(changeSet, child);
        child.tryRemove(changeSet);
    }

    public void rename(ChangeSet changeSet, String name) {

        if (getName().equals(name))
            return;

        Set<KnownFile> parents = new HashSet<>(this.parents.keySet());

        for (KnownFile parent : parents)
            parent.tryDetachChild(changeSet, this);

        self.setName(name);
        save(changeSet, this);

        for (KnownFile parent : parents)
            parent.tryAddChild(changeSet, this);
    }

    public File toFile() {
        return self.clone();
    }

    public void setTracked() {
        children = new HashMap<>();
    }

    private void tryDetachChild(ChangeSet changeSet, KnownFile child) {

        child.self.getParentIds().remove(getId());
        save(changeSet, child);

        if (isTracked()) {
            children.remove(child.parents.get(this));
            child.parents.remove(this);
        }
    }

    private void tryRemove(ChangeSet changeSet) {

        if (parents.size() != 0)
            return;

        knownFiles.remove(this);
        remove(changeSet, this);

        Map<String, KnownFile> children = getChildrenOrNull();
        if (children != null) {
            for (KnownFile child : new LinkedList<>(children.values()))
                tryRemoveChild(changeSet, child);
        }
    }

    private void save(ChangeSet changeSet) {
        save(changeSet, this);
    }

    private static void save(ChangeSet changeSet, KnownFile knownFile) {
        changeSet.put(
                KnownFiles.namespace,
                getDatabaseKeyByFileId(knownFile.getId()), new KnownFileDto(knownFile).serialize());
    }

    private static void remove(ChangeSet changeSet, KnownFile knownFile) {
        changeSet.remove(KnownFiles.namespace, getDatabaseKeyByFileId(knownFile.getId()));
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

    private static String getDatabaseKeyByFileId(FileId fileId) {
        return KnownFiles.fileEntryKeyPrefix + fileId.get();
    }
}
