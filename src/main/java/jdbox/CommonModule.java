package jdbox;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import jdbox.models.fileids.FileIdStore;

public class CommonModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(FileIdStore.class).in(Singleton.class);
    }
}
