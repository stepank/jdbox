package jdbox.localstate;

import com.google.inject.Injector;
import com.google.inject.Singleton;
import jdbox.modules.ActiveModule;

import java.io.IOException;

public class LocalStateModule extends ActiveModule {

    @Override
    protected void configure() {
        bind(LocalState.class).in(Singleton.class);
    }

    @Override
    public void init(Injector injector) throws IOException {
        injector.getInstance(LocalState.class).init();
    }
}
