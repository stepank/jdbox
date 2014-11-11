import com.google.api.services.drive.Drive;
import jdbox.FileSystem;
import jdbox.SetUpForTests;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;

public class BaseFileSystemTest {

    protected static String mountPoint;
    protected static Drive drive;

    protected FileSystem fs;

    @BeforeClass
    public static void setUpClass() throws Exception {
        mountPoint = System.getProperty("user.home") + "/mnt/jdbox-test";
        drive = SetUpForTests.getTestDriveService();
    }

    @Before
    public void setUp() throws Exception {
        fs = new FileSystem(drive);
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
