package jdbox.filetree;

import com.google.api.services.drive.model.ParentReference;
import com.google.common.base.Function;
import com.google.common.collect.Collections2;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Date;
import java.util.concurrent.ConcurrentLinkedQueue;

public class File {

    public static String fields =
            "items(id,title,mimeType,downloadUrl,fileSize,alternateLink,parents,labels,createdDate,modifiedDate,lastViewedByMeDate)";

    private static String alternateLinkText =
            "This file cannot be donloaded directly, you can open it in browser using the following link:\n  ";

    private volatile boolean uploaded;
    private volatile String id;
    private volatile String name;
    private volatile boolean isDirectory;
    private volatile boolean isReal;
    private volatile boolean isTrashed;
    private volatile String downloadUrl;
    private volatile String exportInfo;
    private volatile Date createdDate;
    private volatile Date modifiedDate;
    private volatile Date accessedDate;
    private volatile Collection<String> parentIds;
    private volatile long size;

    public File(String id, String name, final String parentId, boolean isDirectory) {
        this(id, name, parentId, isDirectory, false);
    }

    private File(String id, String name, final String parentId, boolean isDirectory, boolean uploaded) {
        isReal = true;
        this.uploaded = uploaded;
        this.id = id;
        this.name = name;
        this.isDirectory = isDirectory;
        if (parentId != null)
            this.parentIds = new ConcurrentLinkedQueue<String>() {{
                add(parentId);
            }};
    }

    public File(com.google.api.services.drive.model.File file) {

        uploaded = true;

        id = file.getId();
        name = file.getTitle();
        isDirectory = file.getMimeType().equals("application/vnd.google-apps.folder");
        isReal = file.getDownloadUrl() != null && file.getDownloadUrl().length() != 0;
        isTrashed = file.getLabels().getTrashed();
        downloadUrl = file.getDownloadUrl();
        exportInfo = alternateLinkText + file.getAlternateLink() + "\n";
        createdDate = file.getCreatedDate() != null ? new Date(file.getCreatedDate().getValue()) : null;
        modifiedDate = file.getModifiedDate() != null ? new Date(file.getModifiedDate().getValue()) : null;
        accessedDate = file.getLastViewedByMeDate() != null ? new Date(file.getLastViewedByMeDate().getValue()) : null;

        parentIds = new ConcurrentLinkedQueue<>(
                Collections2.transform(file.getParents(), new Function<ParentReference, String>() {
                    @Nullable
                    @Override
                    public String apply(ParentReference parentReference) {
                        return parentReference.getId();
                    }
                }));

        if (isDirectory)
            size = 0;
        else if (!isReal())
            size = getExportInfo().length();
        else
            size = file.getFileSize() == null ? 0 : file.getFileSize();
    }

    public static File getRoot(String id) {
        return new File(id, "/", null, true, true);
    }

    public void updateId(String id) {
        assert !uploaded : "file is already uploaded and has id";
        this.id = id;
        uploaded = true;
    }

    public void update(File file) {

        assert file.getId().equals(id) : "new file id is not equal the original one";

        isReal = file.isReal();
        isTrashed = file.isTrashed();
        downloadUrl = file.getDownloadUrl();
        exportInfo = file.getExportInfo();
        parentIds = file.getParentIds();
        size = file.getSize();
        createdDate = file.getCreatedDate();
        modifiedDate = file.getModifiedDate();
        accessedDate = file.getAccessedDate();
    }

    public boolean isUploaded() {
        return uploaded;
    }

    public String getId() {
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

    public String getExportInfo() {
        return exportInfo;
    }

    public Date getCreatedDate() {
        return createdDate;
    }

    public File setCreatedDate(Date date) {
        createdDate = date;
        return this;
    }

    public Date getAccessedDate() {
        return accessedDate;
    }

    public File setAccessedDate(Date date) {
        accessedDate = date;
        return this;
    }

    public Date getModifiedDate() {
        return modifiedDate;
    }

    public File setModifiedDate(Date date) {
        modifiedDate = date;
        return this;
    }

    public Collection<String> getParentIds() {
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
