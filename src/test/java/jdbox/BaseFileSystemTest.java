package jdbox;

import com.google.api.services.drive.Drive;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;

public class BaseFileSystemTest {

    protected static Drive drive;

    protected FileSystem fs;

    @BeforeClass
    public static void setUpClass() throws Exception {
        drive = SetUpForTests.getTestDriveService();
    }

    @Before
    public void setUp() throws Exception {
        fs = new FileSystem(drive);
    }
}
