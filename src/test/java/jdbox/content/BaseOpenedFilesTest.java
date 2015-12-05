package jdbox.content;

import jdbox.content.bytestores.InMemoryByteStoreFactory;
import jdbox.content.bytestores.StreamCachingByteSourceFactory;
import jdbox.content.filetypes.FullAccessOpenedFileFactory;
import jdbox.content.localstorage.LocalStorage;
import jdbox.driveadapter.DriveAdapter;
import jdbox.models.fileids.FileIdStore;
import jdbox.utils.OrderedRule;
import jdbox.utils.TestFolderProvider;
import org.junit.Before;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

public class BaseOpenedFilesTest extends BaseContentModuleTest {

    @OrderedRule
    public final TestFolderProvider testFolderProvider = new TestFolderProvider(errorCollector, lifeCycleManager);

    protected FileIdStore fileIdStore;
    protected DriveAdapter drive;

    protected FullAccessOpenedFileFactory factory;
    protected InMemoryByteStoreFactory tempStoreFactory;
    protected StreamCachingByteSourceFactory readerFactory;
    protected OpenedFiles openedFiles;

    @Before
    public void setUp() {

        fileIdStore = lifeCycleManager.getInstance(FileIdStore.class);
        drive = lifeCycleManager.getInstance(DriveAdapter.class);

        tempStoreFactory = lifeCycleManager.getInstance(InMemoryByteStoreFactory.class);
        tempStoreFactory.setConfig(new InMemoryByteStoreFactory.Config(4));
        readerFactory = lifeCycleManager.getInstance(StreamCachingByteSourceFactory.class);
        readerFactory.setConfig(new StreamCachingByteSourceFactory.Config(4));
        factory = lifeCycleManager.getInstance(FullAccessOpenedFileFactory.class);
        openedFiles = lifeCycleManager.getInstance(OpenedFiles.class);

        assertThat(lifeCycleManager.getInstance(LocalStorage.class).getFilesCount(), equalTo(0));
    }
}
