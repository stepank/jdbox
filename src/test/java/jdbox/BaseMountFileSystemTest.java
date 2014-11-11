package jdbox;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;

public class BaseMountFileSystemTest extends BaseFileSystemTest {

    protected static String mountPoint;

    @BeforeClass
    public static void setUpClass() throws Exception {
        BaseFileSystemTest.setUpClass();
        mountPoint = System.getProperty("user.home") + "/mnt/jdbox-test";
    }

    @Before
    public void setUp() throws Exception {
        super.setUp();
        fs.mount(new java.io.File(mountPoint), false);
    }

    @After
    public void tearDown() throws Exception {
        fs.unmount();
    }

    public String localPath(String remotePath) {
        return mountPoint + remotePath;
    }
}
