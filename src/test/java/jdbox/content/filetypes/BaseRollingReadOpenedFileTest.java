package jdbox.content.filetypes;

import jdbox.content.BaseOpenedFilesTest;
import jdbox.content.bytestores.InMemoryByteStoreFactory;
import org.junit.Before;

public class BaseRollingReadOpenedFileTest extends BaseOpenedFilesTest {

    protected InMemoryByteStoreFactory tempStoreFactory;
    protected RollingReadOpenedFileFactory factory;

    @Before
    public void setUp() {
        super.setUp();
        tempStoreFactory = lifeCycleManager.getInstance(InMemoryByteStoreFactory.class);
        factory = lifeCycleManager.getInstance(RollingReadOpenedFileFactory.class);
    }
}
