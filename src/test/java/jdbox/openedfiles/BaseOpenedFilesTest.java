package jdbox.openedfiles;

import jdbox.BaseTest;
import org.junit.Before;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

public class BaseOpenedFilesTest extends BaseTest {

    protected FullAccessOpenedFileFactory factory;
    protected InMemoryByteStoreFactory tempStoreFactory;
    protected StreamCachingByteSourceFactory readerFactory;
    protected OpenedFiles openedFiles;

    @Before
    public void setUp() throws Exception {
        autoUpdateFileTree = false;
        super.setUp();
        tempStoreFactory = injector.getInstance(InMemoryByteStoreFactory.class);
        tempStoreFactory.setConfig(new InMemoryByteStoreFactory.Config(4));
        readerFactory = injector.getInstance(StreamCachingByteSourceFactory.class);
        readerFactory.setConfig(new StreamCachingByteSourceFactory.Config(4));
        factory = injector.getInstance(FullAccessOpenedFileFactory.class);
        openedFiles = injector.getInstance(OpenedFiles.class);
        assertThat(injector.getInstance(LocalStorage.class).getFilesCount(), equalTo(0));
    }
}
