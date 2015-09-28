package jdbox.models;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import jdbox.models.fileids.FileId;
import jdbox.models.fileids.FileIdStore;

import java.util.Date;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

public class File implements Cloneable {

    public enum Field {
        NAME,
        SIZE,
        CREATED_DATE,
        MODIFIED_DATE,
        ACCESSED_DATE,
        PARENT_IDS
    }

    private final FileId id;

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
    private Set<FileId> parentIds;
    private boolean isTrashed;

    public File() {
        this.id = null;
    }

    public File(FileId fileId) {
        this.id = fileId;
    }

    public File(FileIdStore fileIdStore) {
        this.id = fileIdStore.create();
    }

    public File(final FileIdStore fileIdStore, jdbox.driveadapter.File file) {

        id = fileIdStore.get(file.getId());
        etag = file.getEtag();
        name = file.getName();
        size = file.getSize();
        isDirectory = file.isDirectory();
        downloadUrl = file.getDownloadUrl();
        alternateLink = file.getAlternateLink();
        createdDate = file.getCreatedDate();
        modifiedDate = file.getModifiedDate();
        accessedDate = file.getAccessedDate();
        mimeType = file.getMimeType();

        parentIds = new HashSet<>(Collections2.transform(file.getParentIds(), new Function<String, FileId>() {
            @Override
            public FileId apply(String parentId) {
                return fileIdStore.get(parentId);
            }
        }));

        isTrashed = file.isTrashed();
    }

    public FileId getId() {
        return id;
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

    public Set<FileId> getParentIds() {
        return parentIds;
    }

    public void setParentIds(Set<FileId> parentIds) {
        this.parentIds = parentIds;
    }

    public boolean isTrashed() {
        return isTrashed;
    }

    public jdbox.driveadapter.File toDaFile() {

        jdbox.driveadapter.File file = new jdbox.driveadapter.File();

        if (id.isSet())
            file.setId(id.get());

        file.setName(getName());
        file.setIsDirectory(isDirectory());
        file.setSize(getSize());
        file.setDownloadUrl(getDownloadUrl());
        file.setCreatedDate(getCreatedDate());
        file.setModifiedDate(getModifiedDate());
        file.setAccessedDate(getAccessedDate());

        if (getParentIds() != null)
            file.setParentIds(new HashSet<>(
                    Collections2.transform(getParentIds(), new Function<FileId, String>() {
                        @Override
                        public String apply(FileId parentId) {
                            return parentId.get();
                        }
                    })));

        return file;
    }

    @Override
    public File clone() {
        try {
            File clone = (File) super.clone();
            if (getParentIds() != null)
                clone.setParentIds(new HashSet<>(getParentIds()));
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }

    public File clone(EnumSet<Field> fields) {
        File file = new File(getId());
        file.update(this, fields);
        return file;
    }

    public void update(File file, EnumSet<Field> fields) {

        setIsDirectory(file.isDirectory());
        setEtag(file.getEtag());

        if (fields.contains(Field.NAME))
            setName(file.getName());

        if (fields.contains(Field.SIZE))
            setSize(file.getSize());

        if (fields.contains(Field.CREATED_DATE))
            setCreatedDate(file.getCreatedDate());

        if (fields.contains(Field.MODIFIED_DATE))
            setModifiedDate(file.getModifiedDate());

        if (fields.contains(Field.ACCESSED_DATE))
            setAccessedDate(file.getAccessedDate());

        if (fields.contains(Field.PARENT_IDS) && file.getParentIds() != null)
            setParentIds(new HashSet<>(file.getParentIds()));
    }

    @Override
    public String toString() {
        return "j.m.File{" +
                "id=" + id +
                ", name=" + (name == null ? "null" : ('\'' + name + '\'')) +
                ", isDirectory=" + isDirectory +
                ", size=" + size +
                ", etag=" + etag +
                '}';
    }
}
