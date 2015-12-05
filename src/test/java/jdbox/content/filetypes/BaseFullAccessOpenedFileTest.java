package jdbox.content.filetypes;

import jdbox.content.BaseOpenedFilesTest;
import jdbox.content.bytestores.InMemoryByteStoreFactory;
import jdbox.content.bytestores.StreamCachingByteSourceFactory;
import org.junit.Before;

public class BaseFullAccessOpenedFileTest extends BaseOpenedFilesTest {

    protected FullAccessOpenedFileFactory factory;
    protected InMemoryByteStoreFactory tempStoreFactory;
    protected StreamCachingByteSourceFactory readerFactory;

    @Before
    public void setUp() {
        super.setUp();
        tempStoreFactory = lifeCycleManager.getInstance(InMemoryByteStoreFactory.class);
        tempStoreFactory.setConfig(new InMemoryByteStoreFactory.Config(4));
        readerFactory = lifeCycleManager.getInstance(StreamCachingByteSourceFactory.class);
        readerFactory.setConfig(new StreamCachingByteSourceFactory.Config(4));
        factory = lifeCycleManager.getInstance(FullAccessOpenedFileFactory.class);
    }
}
