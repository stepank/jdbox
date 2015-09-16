package jdbox.modules;

import com.google.inject.AbstractModule;
import com.google.inject.Injector;

public abstract class ActiveModule extends AbstractModule {

    public void init(Injector injector) throws Exception {
    }

    public void start(Injector injector) {
    }

    public void tearDown(Injector injector) throws Exception {
    }
}
