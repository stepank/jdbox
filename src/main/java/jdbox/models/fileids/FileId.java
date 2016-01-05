package jdbox.models.fileids;

import java.util.concurrent.atomic.AtomicReference;

public class FileId {

    private final FileIdStore store;
    private final AtomicReference<String> id = new AtomicReference<>();

    FileId(FileIdStore store) {
        this.store = store;
    }

    FileId(String value) {
        store = null;
        id.set(value);
    }

    public String get() {
        String result = id.get();
        if (result == null)
            throw new UnsupportedOperationException("id is null");
        return result;
    }

    public void set(String value) {
        if (!id.compareAndSet(null, value))
            throw new UnsupportedOperationException("id can be set only once");
        assert store != null;
        store.put(value, this);
    }

    public boolean isSet() {
        return id.get() != null;
    }

    @Override
    public String toString() {
        return "FileId{" + id + '}';
    }
}