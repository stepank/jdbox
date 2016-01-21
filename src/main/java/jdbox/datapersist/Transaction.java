package jdbox.datapersist;

import com.sleepycat.je.DatabaseException;

class Transaction {

    private final com.sleepycat.je.Transaction transaction;

    public Transaction(com.sleepycat.je.Transaction transaction) {
        this.transaction = transaction;
    }

    com.sleepycat.je.Transaction getOrigin() {
        return transaction;
    }

    public void commit() {
        try {
            transaction.commit();
        } catch (DatabaseException e) {
            throw new StorageException(e);
        }
    }
}
