package jdbox;

import org.ini4j.Ini;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;

public class BaseMountFileSystemTest extends BaseFileSystemTest {

    protected static String mountPoint;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        mountPoint = injector.getInstance(Ini.class).get("Main", "mount_point");
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
