package jdbox.filetree;

import com.google.api.services.drive.model.ParentReference;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

import javax.annotation.Nullable;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

public class File {

    public static String fields =
            "items(id,title,mimeType,downloadUrl,fileSize,alternateLink,parents,labels,createdDate,modifiedDate,lastViewedByMeDate)";

    private static String alternateLinkText =
            "This file cannot be donloaded directly, you can open it in browser using the following link:\n  ";

    private volatile boolean uploaded;
    private volatile String id;
    private volatile String name;
    private volatile boolean isDirectory;
    private volatile boolean isDownloadable;
    private volatile boolean isTrashed;
    private volatile String downloadUrl;
    private volatile String exportInfo;
    private volatile Date createdDate;
    private volatile Date modifiedDate;
    private volatile Date accessedDate;
    private volatile List<String> parentIds;
    private volatile long size;

    public File(String id, String name, final String parentId, boolean isDirectory) {
        this(id, name, parentId, isDirectory, false);
    }

    private File(String id, String name, final String parentId, boolean isDirectory, boolean uploaded) {
        this.uploaded = uploaded;
        this.id = id;
        this.name = name;
        this.isDirectory = isDirectory;
        if (parentId != null)
            this.parentIds = new LinkedList<String>() {{
                add(parentId);
            }};
    }

    public File(com.google.api.services.drive.model.File file) {

        uploaded = true;

        id = file.getId();
        name = file.getTitle();
        isDirectory = file.getMimeType().equals("application/vnd.google-apps.folder");
        isDownloadable = file.getDownloadUrl() != null && file.getDownloadUrl().length() != 0;
        isTrashed = file.getLabels().getTrashed();
        downloadUrl = file.getDownloadUrl();
        exportInfo = alternateLinkText + file.getAlternateLink() + "\n";
        createdDate = file.getCreatedDate() != null ? new Date(file.getCreatedDate().getValue()) : null;
        modifiedDate = file.getModifiedDate() != null ? new Date(file.getModifiedDate().getValue()) : null;
        accessedDate = file.getLastViewedByMeDate() != null ? new Date(file.getLastViewedByMeDate().getValue()) : null;

        parentIds = ImmutableList.copyOf(Iterables.transform(file.getParents(), new Function<ParentReference, String>() {
            @Nullable
            @Override
            public String apply(ParentReference parentReference) {
                return parentReference.getId();
            }
        }));

        if (isDirectory)
            size = 0;
        else if (!isDownloadable())
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

    public void updateName(File file) {
        assert file.getId().equals(id) : "new file id is not equal the original one";
        name = file.getName();
    }

    public void update(File file) {

        assert file.getId().equals(id) : "new file id is not equal the original one";

        isDownloadable = file.isDownloadable();
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

    public boolean isDirectory() {
        return isDirectory;
    }

    public boolean isDownloadable() {
        return isDownloadable;
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

    public void setCreatedDate(Date date) {
        createdDate = date;
    }

    public Date getAccessedDate() {
        return accessedDate;
    }

    public void setAccessedDate(Date date) {
        accessedDate = date;
    }

    public Date getModifiedDate() {
        return modifiedDate;
    }

    public void setModifiedDate(Date date) {
        modifiedDate = date;
    }

    public List<String> getParentIds() {
        return parentIds;
    }

    public long getSize() {
        return size;
    }

    public String toString() {
        return isDirectory() ? String.format("folder %s", getName()) : String.format("file %s", getName());
    }
}

