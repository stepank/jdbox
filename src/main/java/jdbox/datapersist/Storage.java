package jdbox.datapersist;

import com.sleepycat.je.*;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Storage {

    private final Path path;
    private final Map<String, Database> databases = new HashMap<>();

    private Environment environment;

    public Storage(Path path) {
        this.path = path;
    }

    public void init() {

        EnvironmentConfig envConfig = new EnvironmentConfig();
        envConfig.setTransactional(true);
        envConfig.setAllowCreate(true);

        try {
            environment = new Environment(path.toFile(), envConfig);
        } catch (DatabaseException e) {
            throw new StorageException(e);
        }
    }

    public void tearDown() {
        for (Database database : databases.values())
            database.close();
        databases.clear();
    }

    public List<Map.Entry<String, String>> getData(String ns) {
        return getDatabase(ns).getAll();
    }

    public void applyChangeSet(ChangeSet changeSet) {
        Transaction txn = createTransaction();
        changeSet.apply(txn, this);
        txn.commit();
    }

    Database getDatabase(String name) {
        Database database = databases.get(name);
        if (database == null) {
            database = openDatabase(name);
            databases.put(name, database);
        }
        return database;
    }

    Database openDatabase(String name) {

        DatabaseConfig config = new DatabaseConfig();
        config.setTransactional(true);
        config.setAllowCreate(true);

        try {
            return new Database(environment.openDatabase(null, name, config));
        } catch (DatabaseException e) {
            throw new StorageException(e);
        }
    }

    Transaction createTransaction() {

        TransactionConfig config = new TransactionConfig();
        config.setReadCommitted(false);
        config.setReadUncommitted(false);
        config.setSync(true);

        try {
            return new Transaction(environment.beginTransaction(null, config));
        } catch (DatabaseException e) {
            throw new StorageException(e);
        }
    }
}
