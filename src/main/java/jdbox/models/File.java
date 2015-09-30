package jdbox.models;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import jdbox.driveadapter.Field;
import jdbox.models.fileids.FileId;
import jdbox.models.fileids.FileIdStore;

import java.util.Date;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

public class File implements Cloneable {

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
    private boolean isTrashed;
    private Set<FileId> parentIds = new HashSet<>();

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
        isTrashed = file.isTrashed();

        parentIds.addAll(Collections2.transform(file.getParentIds(), new Function<String, FileId>() {
            @Override
            public FileId apply(String parentId) {
                return fileIdStore.get(parentId);
            }
        }));
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

    public boolean isTrashed() {
        return isTrashed;
    }

    public Set<FileId> getParentIds() {
        return parentIds;
    }

    public void setParentIds(Set<FileId> parentIds) {
        if (parentIds == null)
            throw new IllegalArgumentException("parentIds must not be empty");
        this.parentIds = parentIds;
    }

    public jdbox.driveadapter.File toDaFile() {
        return toDaFile(EnumSet.noneOf(Field.class));
    }

    public jdbox.driveadapter.File toDaFile(EnumSet<Field> fields) {

        jdbox.driveadapter.File file = new jdbox.driveadapter.File();

        if (id.isSet())
            file.setId(id.get());

        if (fields.contains(Field.NAME))
            file.setName(getName());

        if (fields.contains(Field.IS_DIRECTORY))
            file.setIsDirectory(isDirectory());

        if (fields.contains(Field.SIZE))
            file.setSize(getSize());

        if (fields.contains(Field.CREATED_DATE))
            file.setCreatedDate(getCreatedDate());

        if (fields.contains(Field.MODIFIED_DATE))
            file.setModifiedDate(getModifiedDate());

        if (fields.contains(Field.ACCESSED_DATE))
            file.setAccessedDate(getAccessedDate());

        if (fields.contains(Field.DOWNLOAD_URL))
            file.setDownloadUrl(getDownloadUrl());

        if (fields.contains(Field.PARENT_IDS))
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
            clone.setParentIds(new HashSet<>(getParentIds()));
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }

    public void update(File file) {
        setIsDirectory(file.isDirectory());
        setEtag(file.getEtag());
        setSize(file.getSize());
        setCreatedDate(file.getCreatedDate());
        setModifiedDate(file.getModifiedDate());
        setAccessedDate(file.getAccessedDate());
    }

    @Override
    public String toString() {
        return "j.m.File{" +
                "id=" + id +
                ", name=" + (name == null ? "null" : ('\'' + name + '\'')) +
                ", isDirectory=" + isDirectory +
                ", size=" + size +
                ", parents=" + parentIds.size() +
                ", etag=" + etag +
                '}';
    }
}
