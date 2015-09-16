package jdbox.openedfiles;

import org.junit.Before;

public class BaseRollingReadOpenedFileTest extends BaseOpenedFilesTest {

    protected InMemoryByteStoreFactory tempStoreFactory;
    protected StreamCachingByteSourceFactory readerFactory;
    protected RollingReadOpenedFileFactory factory;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        tempStoreFactory = lifeCycleManager.getInstance(InMemoryByteStoreFactory.class);
        readerFactory = lifeCycleManager.getInstance(StreamCachingByteSourceFactory.class);
        factory = lifeCycleManager.getInstance(RollingReadOpenedFileFactory.class);
    }
}
