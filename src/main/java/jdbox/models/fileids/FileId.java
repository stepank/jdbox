package jdbox.models.fileids;

import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public class FileId {

    private final FileIdStore store;
    private final AtomicReference<String> remoteId;
    private final String localId = UUID.randomUUID().toString();

    FileId(FileIdStore store, AtomicReference<String> remoteId) {
        this.store = store;
        this.remoteId = remoteId;
    }

    FileId(FileIdStore store) {
        this(store, new AtomicReference<String>());
    }

    FileId(FileIdStore store, String id) {
        this(store, new AtomicReference<>(id));
        if (id == null)
            throw new IllegalArgumentException("id");
    }

    public String get() {
        String result = remoteId.get();
        if (result == null)
            throw new UnsupportedOperationException("id is null");
        return result;
    }

    public void set(String value) {
        if (!remoteId.compareAndSet(null, value))
            throw new UnsupportedOperationException("id can be set only once");
        store.put(this);
    }

    public boolean isSet() {
        return remoteId.get() != null;
    }

    @Override
    public String toString() {
        return "FileId{" + localId + ' '  + remoteId.get() + '}';
    }

    public String serialize() {
        throw new NotImplementedException();
    }

    public static FileId deserialize(String data) {
        throw new NotImplementedException();
    }
}