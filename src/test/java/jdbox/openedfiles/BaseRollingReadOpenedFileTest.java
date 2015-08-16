package jdbox.openedfiles;

import org.junit.Before;

public class BaseRollingReadOpenedFileTest extends BaseOpenedFilesTest {

    protected InMemoryByteStoreFactory tempStoreFactory;
    protected StreamCachingByteSourceFactory readerFactory;
    protected RollingReadOpenedFileFactory factory;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        tempStoreFactory = injector.getInstance(InMemoryByteStoreFactory.class);
        readerFactory = injector.getInstance(StreamCachingByteSourceFactory.class);
        factory = injector.getInstance(RollingReadOpenedFileFactory.class);
    }
}
