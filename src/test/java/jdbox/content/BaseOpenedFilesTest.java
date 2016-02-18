package jdbox.content;

import jdbox.content.bytestores.InMemoryByteStoreFactory;
import jdbox.content.filetypes.FullAccessOpenedFileFactory;
import jdbox.content.localstorage.LocalStorage;
import jdbox.models.fileids.FileIdStore;
import org.junit.Before;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

public class BaseOpenedFilesTest extends BaseContentModuleTest {

    protected FileIdStore fileIdStore;

    protected FullAccessOpenedFileFactory factory;
    protected InMemoryByteStoreFactory tempStoreFactory;
    protected OpenedFiles openedFiles;

    @Before
    public void setUp() {

        fileIdStore = lifeCycleManager.getInstance(FileIdStore.class);

        tempStoreFactory = lifeCycleManager.getInstance(InMemoryByteStoreFactory.class);
        tempStoreFactory.setConfig(new InMemoryByteStoreFactory.Config(4));
        factory = lifeCycleManager.getInstance(FullAccessOpenedFileFactory.class);
        factory.setConfig(new FullAccessOpenedFileFactory.Config(4));
        openedFiles = lifeCycleManager.getInstance(OpenedFiles.class);

        assertThat(lifeCycleManager.getInstance(LocalStorage.class).getFilesCount(), equalTo(0));
    }
}
