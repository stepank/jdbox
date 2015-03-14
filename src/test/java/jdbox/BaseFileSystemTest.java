package jdbox;

import org.junit.Before;

public class BaseFileSystemTest extends BaseTest {

    protected FileSystem fs;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        fs = injector.getInstance(FileSystem.class);
    }
}
