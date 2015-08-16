package jdbox.openedfiles;

import org.junit.Before;

public class BaseFullAccessOpenedFileTest extends BaseOpenedFilesTest {

    protected FullAccessOpenedFileFactory factory;
    protected InMemoryByteStoreFactory tempStoreFactory;
    protected StreamCachingByteSourceFactory readerFactory;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        tempStoreFactory = injector.getInstance(InMemoryByteStoreFactory.class);
        tempStoreFactory.setConfig(new InMemoryByteStoreFactory.Config(4));
        readerFactory = injector.getInstance(StreamCachingByteSourceFactory.class);
        readerFactory.setConfig(new StreamCachingByteSourceFactory.Config(4));
        factory = injector.getInstance(FullAccessOpenedFileFactory.class);
    }
}
