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
        FileId newId = new FileId(this, id);
        FileId existingId = entries.putIfAbsent(id, newId);
        return existingId != null ? existingId : newId;
    }

    void put(FileId fileId) {
        if (entries.putIfAbsent(fileId.get(), fileId) != null)
            throw new ConcurrentModificationException("trying to create two FileIds with equal values");
    }
}
