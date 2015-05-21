package jdbox.models.fileids;

public class FileId {

    private final FileIdStore store;

    private volatile String id;

    FileId(FileIdStore store) {
        this.store = store;
    }

    FileId(String id) {
        store = null;
        this.id = id;
    }

    public String get() {
        if (!isSet())
            throw new UnsupportedOperationException("id is null");
        return id;
    }

    public void set(String id) {
        if (isSet())
            throw new UnsupportedOperationException("id can be set only once");
        store.put(id, this);
        this.id = id;
    }

    public boolean isSet() {
        return id != null;
    }

    @Override
    public String toString() {
        return "FileId{" + id + '}';
    }
}