package jdbox;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;

public class ApplicationModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(FileSystem.class).in(Singleton.class);
    }
}
