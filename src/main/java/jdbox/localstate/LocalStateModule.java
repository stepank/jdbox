package jdbox.localstate;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;

public class LocalStateModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(LocalState.class).in(Singleton.class);
    }
}
