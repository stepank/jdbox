package jdbox.uploader;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;

public class UploaderModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(Uploader.class).in(Singleton.class);
    }
}
