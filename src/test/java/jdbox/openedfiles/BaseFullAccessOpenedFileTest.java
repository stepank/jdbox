package jdbox.openedfiles;

import jdbox.BaseTest;
import org.junit.Before;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class BaseFullAccessOpenedFileTest extends BaseTest {

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
        assertThat(factory.getSharedFilesCount(), equalTo(0));
    }
}
