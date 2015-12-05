package jdbox.content.filetypes;

import jdbox.content.BaseOpenedFilesTest;
import jdbox.content.bytestores.InMemoryByteStoreFactory;
import jdbox.content.bytestores.StreamCachingByteSourceFactory;
import org.junit.Before;

public class BaseRollingReadOpenedFileTest extends BaseOpenedFilesTest {

    protected InMemoryByteStoreFactory tempStoreFactory;
    protected StreamCachingByteSourceFactory readerFactory;
    protected RollingReadOpenedFileFactory factory;

    @Before
    public void setUp() {
        super.setUp();
        tempStoreFactory = lifeCycleManager.getInstance(InMemoryByteStoreFactory.class);
        readerFactory = lifeCycleManager.getInstance(StreamCachingByteSourceFactory.class);
        factory = lifeCycleManager.getInstance(RollingReadOpenedFileFactory.class);
    }
}
