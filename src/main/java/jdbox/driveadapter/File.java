package jdbox.driveadapter;

import com.google.api.client.util.DateTime;
import com.google.api.services.drive.model.ParentReference;
import com.google.common.base.Function;
import com.google.common.collect.Collections2;

import java.util.Date;
import java.util.LinkedList;
import java.util.Set;
import java.util.TreeSet;

public class File {

    public final static String fields =
            "id,etag,title,mimeType,downloadUrl,fileSize,alternateLink,parents,labels," +
                    "createdDate,modifiedDate,lastViewedByMeDate,md5Checksum";

    private String id;
    private String etag;
    private String name;
    private boolean isDirectory;
    private long size;
    private String downloadUrl;
    private String alternateLink;
    private Date createdDate;
    private Date modifiedDate;
    private Date accessedDate;
    private String mimeType;
    private boolean isTrashed;
    private Set<String> parentIds;
    private String md5Sum;

    public File() {
    }

    public File(com.google.api.services.drive.model.File file) {

        id = file.getId();
        etag = file.getEtag();
        name = file.getTitle();
        size = file.getFileSize() != null ? file.getFileSize() : 0;
        isDirectory = file.getMimeType().equals("application/vnd.google-apps.folder");
        downloadUrl = file.getDownloadUrl();
        alternateLink = file.getAlternateLink();
        createdDate = file.getCreatedDate() != null ? new Date(file.getCreatedDate().getValue()) : null;
        modifiedDate = file.getModifiedDate() != null ? new Date(file.getModifiedDate().getValue()) : null;
        accessedDate = file.getLastViewedByMeDate() != null ? new Date(file.getLastViewedByMeDate().getValue()) : null;
        mimeType = file.getMimeType();
        isTrashed = file.getLabels().getTrashed();
        md5Sum = file.getMd5Checksum();

        parentIds = new TreeSet<>(
                Collections2.transform(file.getParents(), new Function<ParentReference, String>() {
                    @Override
                    public String apply(ParentReference parentReference) {
                        return parentReference.getId();
                    }
                }));
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getEtag() {
        return etag;
    }

    public void setEtag(String etag) {
        this.etag = etag;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isDirectory() {
        return isDirectory;
    }

    public void setIsDirectory(boolean isDirectory) {
        this.isDirectory = isDirectory;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public void setDownloadUrl(String downloadUrl) {
        this.downloadUrl = downloadUrl;
    }

    public String getAlternateLink() {
        return alternateLink;
    }

    public Date getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(Date date) {
        createdDate = date;
    }

    public Date getModifiedDate() {
        return modifiedDate;
    }

    public void setModifiedDate(Date date) {
        modifiedDate = date;
    }

    public Date getAccessedDate() {
        return accessedDate;
    }

    public void setAccessedDate(Date date) {
        accessedDate = date;
    }

    public String getMimeType() {
        return mimeType;
    }

    public boolean isTrashed() {
        return isTrashed;
    }

    public Set<String> getParentIds() {
        return parentIds;
    }

    public void setParentId(String parentId) {
        this.parentIds = new TreeSet<>();
        this.parentIds.add(parentId);
    }

    public void setParentIds(Set<String> parentIds) {
        if (parentIds == null)
            throw new IllegalArgumentException("parentIds must not be null");
        this.parentIds = parentIds;
    }

    public String getMd5Sum() {
        return md5Sum;
    }

    public com.google.api.services.drive.model.File toGdFile() {

        com.google.api.services.drive.model.File file = new com.google.api.services.drive.model.File();

        if (getName() != null)
            file.setTitle(getName());

        if (isDirectory())
            file.setMimeType("application/vnd.google-apps.folder");

        if (getCreatedDate() != null)
            file.setCreatedDate(new DateTime(getCreatedDate()));

        if (getModifiedDate() != null) {
            file.setModifiedDate(new DateTime(getModifiedDate()));
            file.setModifiedByMeDate(new DateTime(getModifiedDate()));
        }

        if (getAccessedDate() != null)
            file.setLastViewedByMeDate(new DateTime(getAccessedDate()));

        if (getParentIds() != null)
            file.setParents(
                    new LinkedList<>(Collections2.transform(getParentIds(), new Function<String, ParentReference>() {
                        @Override
                        public ParentReference apply(String parentId) {
                            return new ParentReference().setId(parentId);
                        }
                    })));

        return file;
    }

    @Override
    public String toString() {
        return "j.d.File{" +
                "id=" + id +
                ", name=" + (name == null ? "null" : ('\'' + name + '\'')) +
                ", isDirectory=" + isDirectory +
                ", size=" + size +
                ", etag=" + etag +
                '}';
    }
}
