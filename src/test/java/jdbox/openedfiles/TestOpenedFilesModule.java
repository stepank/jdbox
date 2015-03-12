package jdbox.openedfiles;

public class TestOpenedFilesModule extends OpenedFilesModule {

    @Override
    protected void configure() {
        super.configure();
        bind(RangeMappedOpenedFileFactory.Config.class).toInstance(RangeMappedOpenedFileFactory.defaultConfig);
    }
}
