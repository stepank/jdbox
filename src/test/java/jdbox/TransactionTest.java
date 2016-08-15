package jdbox;

import com.sleepycat.je.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

public class TransactionTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private Database db;
    private com.sleepycat.je.Environment environment;

    @Before
    public void setUp() throws DatabaseException {

        EnvironmentConfig envConfig = new EnvironmentConfig();
        envConfig.setTransactional(true);
        envConfig.setAllowCreate(true);

        environment = new com.sleepycat.je.Environment(temporaryFolder.getRoot().toPath().toFile(), envConfig);

        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setTransactional(true);
        dbConfig.setAllowCreate(true);
        db = environment.openDatabase(null, "test_db", dbConfig);
    }

    @After
    public void tearDown() throws DatabaseException {
        db.close();
    }

    @Test
    public void commit() throws DatabaseException {

        DatabaseEntry entry = new DatabaseEntry();

        Transaction txn = beginTransaction();

        db.put(txn, entry("k"), entry("v"));

        assertThat(db.get(txn, entry("k"), entry, LockMode.DEFAULT), equalTo(OperationStatus.SUCCESS));
        assertThat(entry, equalTo(entry("v")));

        assertThat(db.get(null, entry("k"), entry, LockMode.READ_UNCOMMITTED), equalTo(OperationStatus.SUCCESS));
        assertThat(entry, equalTo(entry("v")));

        txn.commit();

        assertThat(db.get(null, entry("k"), entry, LockMode.DEFAULT), equalTo(OperationStatus.SUCCESS));
        assertThat(entry, equalTo(entry("v")));
    }

    @Test
    public void abort() throws DatabaseException {

        DatabaseEntry entry = new DatabaseEntry();

        Transaction txn = beginTransaction();

        db.put(txn, entry("k"), entry("v"));

        assertThat(db.get(txn, entry("k"), entry, LockMode.DEFAULT), equalTo(OperationStatus.SUCCESS));
        assertThat(entry, equalTo(entry("v")));

        txn.abort();

        assertThat(db.get(null, entry("k"), entry, LockMode.DEFAULT), equalTo(OperationStatus.NOTFOUND));
    }

    @Test
    public void autoCommitWithinAbortedTransaction() throws DatabaseException {

        DatabaseEntry entry = new DatabaseEntry();

        Transaction txn = beginTransaction();

        db.put(txn, entry("k"), entry("v"));

        assertThat(db.get(txn, entry("k"), entry, LockMode.DEFAULT), equalTo(OperationStatus.SUCCESS));
        assertThat(entry, equalTo(entry("v")));

        db.put(null, entry("k2"), entry("v2"));
        assertThat(db.get(null, entry("k2"), entry, LockMode.DEFAULT), equalTo(OperationStatus.SUCCESS));
        assertThat(entry, equalTo(entry("v2")));

        txn.abort();

        assertThat(db.get(null, entry("k"), entry, LockMode.DEFAULT), equalTo(OperationStatus.NOTFOUND));

        assertThat(db.get(null, entry("k2"), entry, LockMode.DEFAULT), equalTo(OperationStatus.SUCCESS));
        assertThat(entry, equalTo(entry("v2")));
    }

    @Test
    public void autoCommitWithinCommittedTransaction() throws DatabaseException {

        DatabaseEntry entry = new DatabaseEntry();

        Transaction txn = beginTransaction();

        db.put(txn, entry("k"), entry("v"));

        assertThat(db.get(txn, entry("k"), entry, LockMode.DEFAULT), equalTo(OperationStatus.SUCCESS));
        assertThat(entry, equalTo(entry("v")));

        db.put(null, entry("k2"), entry("v2"));
        assertThat(db.get(null, entry("k2"), entry, LockMode.DEFAULT), equalTo(OperationStatus.SUCCESS));
        assertThat(entry, equalTo(entry("v2")));

        txn.commit();

        assertThat(db.get(null, entry("k"), entry, LockMode.DEFAULT), equalTo(OperationStatus.SUCCESS));
        assertThat(entry, equalTo(entry("v")));

        assertThat(db.get(null, entry("k2"), entry, LockMode.DEFAULT), equalTo(OperationStatus.SUCCESS));
        assertThat(entry, equalTo(entry("v2")));
    }

    @Test
    public void crossDbCommit() throws DatabaseException {

        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setTransactional(true);
        dbConfig.setAllowCreate(true);
        Database db2 = environment.openDatabase(null, "test_db_2", dbConfig);

        DatabaseEntry entry = new DatabaseEntry();

        Transaction txn = beginTransaction();

        db.put(txn, entry("k"), entry("v"));

        assertThat(db.get(txn, entry("k"), entry, LockMode.DEFAULT), equalTo(OperationStatus.SUCCESS));
        assertThat(entry, equalTo(entry("v")));

        db2.put(txn, entry("k2"), entry("v2"));

        assertThat(db2.get(txn, entry("k2"), entry, LockMode.DEFAULT), equalTo(OperationStatus.SUCCESS));
        assertThat(entry, equalTo(entry("v2")));

        txn.commit();

        assertThat(db.get(null, entry("k"), entry, LockMode.DEFAULT), equalTo(OperationStatus.SUCCESS));
        assertThat(entry, equalTo(entry("v")));
        assertThat(db2.get(null, entry("k2"), entry, LockMode.DEFAULT), equalTo(OperationStatus.SUCCESS));
        assertThat(entry, equalTo(entry("v2")));
    }

    @Test
    public void crossDbAbort() throws DatabaseException {

        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setTransactional(true);
        dbConfig.setAllowCreate(true);
        Database db2 = environment.openDatabase(null, "test_db_2", dbConfig);

        DatabaseEntry entry = new DatabaseEntry();

        Transaction txn = beginTransaction();

        db.put(txn, entry("k"), entry("v"));

        assertThat(db.get(txn, entry("k"), entry, LockMode.DEFAULT), equalTo(OperationStatus.SUCCESS));
        assertThat(entry, equalTo(entry("v")));

        db2.put(txn, entry("k2"), entry("v2"));

        assertThat(db2.get(txn, entry("k2"), entry, LockMode.DEFAULT), equalTo(OperationStatus.SUCCESS));
        assertThat(entry, equalTo(entry("v2")));

        txn.abort();

        assertThat(db.get(null, entry("k"), entry, LockMode.DEFAULT), equalTo(OperationStatus.NOTFOUND));
        assertThat(db2.get(null, entry("k2"), entry, LockMode.DEFAULT), equalTo(OperationStatus.NOTFOUND));
    }

    private DatabaseEntry entry(String value) {
        return new DatabaseEntry(value.getBytes());
    }

    private Transaction beginTransaction() throws DatabaseException {
        TransactionConfig txnConfig = new TransactionConfig();
        txnConfig.setReadCommitted(false);
        txnConfig.setReadUncommitted(false);
        txnConfig.setSync(true);
        return environment.beginTransaction(null, txnConfig);
    }
}
