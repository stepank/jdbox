package jdbox.openedfiles;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;

public class OpenedFilesModule extends AbstractModule {

    @Override
    protected void configure() {

        bind(OpenedFiles.class).in(Singleton.class);

        bind(NonDownloadableOpenedFileFactory.class).in(Singleton.class);

        bind(RollingReadOpenedFileFactory.Config.class).toInstance(RollingReadOpenedFileFactory.defaultConfig);
        bind(RollingReadOpenedFileFactory.class).in(Singleton.class);

        bind(RangeMappedOpenedFileFactory.Config.class).toInstance(RangeMappedOpenedFileFactory.defaultConfig);
        bind(RangeMappedOpenedFileFactory.class).in(Singleton.class);
    }
}
