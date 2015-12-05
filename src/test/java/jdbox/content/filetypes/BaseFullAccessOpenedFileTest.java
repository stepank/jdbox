package jdbox.content.filetypes;

import jdbox.content.BaseOpenedFilesTest;
import jdbox.content.bytestores.InMemoryByteStoreFactory;
import org.junit.Before;

public class BaseFullAccessOpenedFileTest extends BaseOpenedFilesTest {

    protected FullAccessOpenedFileFactory factory;
    protected InMemoryByteStoreFactory tempStoreFactory;

    @Before
    public void setUp() {
        super.setUp();
        tempStoreFactory = lifeCycleManager.getInstance(InMemoryByteStoreFactory.class);
        tempStoreFactory.setConfig(new InMemoryByteStoreFactory.Config(4));
        factory = lifeCycleManager.getInstance(FullAccessOpenedFileFactory.class);
        factory.setConfig(new FullAccessOpenedFileFactory.Config(4));
    }
}
