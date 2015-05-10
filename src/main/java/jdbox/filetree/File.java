package jdbox.filetree;

import com.google.api.services.drive.model.ParentReference;
import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import jdbox.openedfiles.NonDownloadableOpenedFile;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Date;
import java.util.concurrent.ConcurrentLinkedQueue;

public class File {

    public static String fields =
            "items(id,title,mimeType,downloadUrl,fileSize,alternateLink,parents,labels,createdDate,modifiedDate,lastViewedByMeDate)";

    private final FileId id;

    private volatile String name;
    private volatile boolean isDirectory;
    private volatile boolean isReal;
    private volatile boolean isTrashed;
    private volatile String downloadUrl;
    private volatile String alternateLink;
    private volatile Date createdDate;
    private volatile Date modifiedDate;
    private volatile Date accessedDate;
    private volatile String mimeType;
    private volatile Collection<FileId> parentIds;
    private volatile long size;

    public static File getRoot(String id) {
        return new File(new FileId(id), "/", null, true);
    }

    public File(String name, File parent, boolean isDirectory) {
        this(new FileId(), name, parent, isDirectory);
    }

    private File(FileId id, String name, final File parent, boolean isDirectory) {
        isReal = true;
        this.id = id;
        this.name = name;
        this.isDirectory = isDirectory;
        if (parent != null)
            this.parentIds = new ConcurrentLinkedQueue<FileId>() {{
                add(parent.getId());
            }};
    }

    public File(com.google.api.services.drive.model.File file) {

        id = new FileId(file.getId());
        name = file.getTitle();
        isDirectory = file.getMimeType().equals("application/vnd.google-apps.folder");
        isReal = file.getDownloadUrl() != null && file.getDownloadUrl().length() != 0;
        isTrashed = file.getLabels().getTrashed();
        downloadUrl = file.getDownloadUrl();
        alternateLink = file.getAlternateLink();
        createdDate = file.getCreatedDate() != null ? new Date(file.getCreatedDate().getValue()) : null;
        modifiedDate = file.getModifiedDate() != null ? new Date(file.getModifiedDate().getValue()) : null;
        accessedDate = file.getLastViewedByMeDate() != null ? new Date(file.getLastViewedByMeDate().getValue()) : null;
        mimeType = file.getMimeType();

        parentIds = new ConcurrentLinkedQueue<>(
                Collections2.transform(file.getParents(), new Function<ParentReference, FileId>() {
                    @Nullable
                    @Override
                    public FileId apply(ParentReference parentReference) {
                        return new FileId(parentReference.getId());
                    }
                }));

        if (isDirectory)
            size = 0;
        else if (!isReal())
            size = NonDownloadableOpenedFile.getContent(this).length();
        else
            size = file.getFileSize() == null ? 0 : file.getFileSize();
    }

    public void setUploaded(File file) {
        this.id.set(file.getId().get());
        this.downloadUrl = file.getDownloadUrl();
    }

    public void update(File file) {

        try {
            assert file.getId().get().equals(id.get()) : "new file id is not equal the original one";
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        isReal = file.isReal();
        isTrashed = file.isTrashed();
        downloadUrl = file.getDownloadUrl();
        alternateLink = file.getAlternateLink();
        parentIds = file.getParentIds();
        size = file.getSize();
        createdDate = file.getCreatedDate();
        modifiedDate = file.getModifiedDate();
        accessedDate = file.getAccessedDate();
        mimeType = file.getMimeType();
    }

    public FileId getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public File setName(String newName) {
        name = newName;
        return this;
    }

    public boolean isDirectory() {
        return isDirectory;
    }

    public boolean isReal() {
        return isReal;
    }

    public boolean isTrashed() {
        return isTrashed;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public String getAlternateLink() {
        return alternateLink;
    }

    public Date getCreatedDate() {
        return createdDate;
    }

    public File setCreatedDate(Date date) {
        createdDate = date;
        return this;
    }

    public Date getModifiedDate() {
        return modifiedDate;
    }

    public File setModifiedDate(Date date) {
        modifiedDate = date;
        return this;
    }

    public Date getAccessedDate() {
        return accessedDate;
    }

    public File setAccessedDate(Date date) {
        accessedDate = date;
        return this;
    }

    public String getMimeType() {
        return mimeType;
    }

    public Collection<FileId> getParentIds() {
        return parentIds;
    }

    public long getSize() {
        return size;
    }

    public File setSize(long size) {
        this.size = size;
        return this;
    }

    public String toString() {
        return isDirectory() ? String.format("folder %s", getName()) : String.format("file %s", getName());
    }
}
