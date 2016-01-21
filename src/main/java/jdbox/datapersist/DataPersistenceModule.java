package jdbox.datapersist;

import com.google.inject.Injector;
import jdbox.modules.ActiveModule;

import java.nio.file.Path;

public class DataPersistenceModule extends ActiveModule {

    private final Path path;

    public DataPersistenceModule(Path path) {
        this.path = path;
    }

    @Override
    protected void configure() {
        bind(Storage.class).toInstance(new Storage(path));
    }

    @Override
    public void init(Injector injector) {
        injector.getInstance(Storage.class).init();
    }

    @Override
    public void tearDown(Injector injector) {
        injector.getInstance(Storage.class).tearDown();
    }
}
