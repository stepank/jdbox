package jdbox.openedfiles;

import jdbox.driveadapter.DriveAdapter;
import jdbox.models.fileids.FileIdStore;
import jdbox.utils.OrderedRule;
import jdbox.utils.TestFolderProvider;
import org.junit.Before;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

public class BaseOpenedFilesTest extends BaseOpenedFilesModuleTest {

    @OrderedRule
    public TestFolderProvider testFolderProvider = new TestFolderProvider(errorCollector, injectorProvider);

    protected FileIdStore fileIdStore;
    protected DriveAdapter drive;

    protected FullAccessOpenedFileFactory factory;
    protected InMemoryByteStoreFactory tempStoreFactory;
    protected StreamCachingByteSourceFactory readerFactory;
    protected OpenedFiles openedFiles;

    @Before
    public void setUp() throws Exception {

        super.setUp();

        fileIdStore = injectorProvider.getInjector().getInstance(FileIdStore.class);
        drive = injectorProvider.getInjector().getInstance(DriveAdapter.class);

        tempStoreFactory = injector.getInstance(InMemoryByteStoreFactory.class);
        tempStoreFactory.setConfig(new InMemoryByteStoreFactory.Config(4));
        readerFactory = injector.getInstance(StreamCachingByteSourceFactory.class);
        readerFactory.setConfig(new StreamCachingByteSourceFactory.Config(4));
        factory = injector.getInstance(FullAccessOpenedFileFactory.class);
        openedFiles = injector.getInstance(OpenedFiles.class);

        assertThat(injector.getInstance(LocalStorage.class).getFilesCount(), equalTo(0));
    }
}
