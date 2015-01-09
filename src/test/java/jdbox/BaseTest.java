package jdbox;

import com.google.api.services.drive.Drive;
import com.google.inject.Injector;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

public class BaseTest {

    private static JdBox.Environment env;
    private static Drive driveService;

    protected final static String testFolderName = "test_folder";
    protected final static String testFileName = "test_file";
    protected final static String testContentString = "hello world";

    protected Injector injector;

    protected DriveAdapter drive;
    protected jdbox.filetree.File testDir;

    @BeforeClass
    public static void setUpClass() throws Exception {
        env = SetUpTests.createEnvironment();
        driveService = JdBox.createDriveService(env);
    }

    @Before
    public void setUp() throws Exception {
        injector = createInjector();
        drive = injector.getInstance(DriveAdapter.class);
        testDir = drive.createFolder(UUID.randomUUID().toString());
    }

    @After
    public void tearDown() throws Exception {
        drive.deleteFile(testDir);
    }

    protected Injector createInjector() throws Exception {
        return JdBox.createInjector(env, driveService, false);
    }

    protected static InputStream getTestContent() {
        return new ByteArrayInputStream(testContentString.getBytes());
    }
}
