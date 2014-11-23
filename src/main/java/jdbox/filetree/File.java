package jdbox.filetree;

import com.google.api.services.drive.model.ParentReference;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

import javax.annotation.Nullable;
import java.util.Date;
import java.util.List;

public class File {

    public static String fields =
            "items(id,title,mimeType,downloadUrl,fileSize,alternateLink,parents,labels,createdDate,modifiedDate,lastViewedByMeDate)";

    private static String alternateLinkText =
            "This file cannot be donloaded directly, you can open it in browser using the following link:\n  ";

    protected volatile String id;
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

    private File(String id, String name, boolean isDirectory) {
        this.id = id;
        this.name = name;
        this.isDirectory = isDirectory;
    }

    public File(com.google.api.services.drive.model.File file) {

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
        return new File(id, "/", true);
    }

    public void update(File file) {

        if (!file.getId().equals(id))
            throw new IllegalArgumentException("new file id is not equal the original one");

        name = file.getName();
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

    public Date getModifiedDate() {
        return modifiedDate;
    }

    public Date getAccessedDate() {
        return accessedDate;
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

