package jdbox.datapersist;

import java.util.HashMap;
import java.util.Map;

public class ChangeSet {

    private final Map<String, Map<String, String>> changes = new HashMap<>();

    public void put(String ns, String key, String value) {
        createOrGetNsChanges(ns).put(key, value);
    }

    public void remove(String ns, String key) {
        createOrGetNsChanges(ns).put(key, null);
    }

    void apply(Transaction txn, Storage storage) {
        for (Map.Entry<String, Map<String, String>> nsChanges : changes.entrySet()) {
            Database database = storage.getDatabase(nsChanges.getKey());
            for (Map.Entry<String, String> change : nsChanges.getValue().entrySet()) {
                if (change.getValue() == null)
                    database.remove(txn, change.getKey());
                else
                    database.put(txn, change.getKey(), change.getValue());
            }
        }
    }

    private Map<String, String> createOrGetNsChanges(String ns) {
        Map<String, String> nsChanges = changes.get(ns);
        if (nsChanges == null) {
            nsChanges = new HashMap<>();
            changes.put(ns, nsChanges);
        }
        return nsChanges;
    }
}
