package jdbox.datapersist;

import com.sleepycat.je.DatabaseException;

public class StorageException extends RuntimeException {
    public StorageException(DatabaseException exception) {
        super(exception);
    }
}
