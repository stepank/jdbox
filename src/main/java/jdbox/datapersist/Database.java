package jdbox.datapersist;

import com.sleepycat.je.*;

import java.util.ArrayList;
import java.util.List;

class Database {

    private final com.sleepycat.je.Database database;

    public Database(com.sleepycat.je.Database database) {
        this.database = database;
    }

    public void put(Transaction txn, String key, String data) {
        try {
            database.put(txn.getOrigin(), new DatabaseEntry(key.getBytes()), new DatabaseEntry(data.getBytes()));
        } catch (DatabaseException e) {
            throw new StorageException(e);
        }
    }

    public void remove(Transaction txn, String key) {
        try {
            database.removeSequence(txn.getOrigin(), new DatabaseEntry(key.getBytes()));
        } catch (DatabaseException e) {
            throw new StorageException(e);
        }
    }

    public List<Entry> getAll() {

        try {

            Cursor cursor = database.openCursor(null, null);

            DatabaseEntry keyEntry = new DatabaseEntry();
            DatabaseEntry dataEntry = new DatabaseEntry();

            ArrayList<Entry> entries = new ArrayList<>();
            while (cursor.getNext(keyEntry, dataEntry, LockMode.DEFAULT) == OperationStatus.SUCCESS)
                entries.add(new Entry(new String(keyEntry.getData()), new String(dataEntry.getData())));

            cursor.close();

            return entries;

        } catch (DatabaseException e) {
            throw new StorageException(e);
        }
    }

    public void close() {
        try {
            database.close();
        } catch (DatabaseException e) {
            throw new StorageException(e);
        }
    }
}
