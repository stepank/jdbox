package jdbox.models.fileids;

import com.google.common.collect.MapMaker;

import java.util.ConcurrentModificationException;
import java.util.concurrent.ConcurrentMap;

public class FileIdStore {

    private final ConcurrentMap<String, FileId> entries;

    public FileIdStore() {
        this.entries = new MapMaker().weakValues().makeMap();
    }

    public FileId create() {
        return new FileId(this);
    }

    public FileId get(String id) {
        if (id == null)
            throw new IllegalArgumentException("id");
        FileId newId = new FileId(id);
        FileId existingId = entries.putIfAbsent(id, newId);
        return existingId != null ? existingId : newId;
    }

    void put(String id, FileId fileId) {
        if (id == null)
            throw new IllegalArgumentException("id");
        FileId existingId = entries.putIfAbsent(id, fileId);
        if (existingId != null)
            throw new ConcurrentModificationException("trying to create two FileIds with equal values");
    }
}
