package jdbox;

import com.google.api.services.drive.Drive;
import com.google.inject.Injector;
import jdbox.openedfiles.RangeMappedOpenedFileFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.lessThan;

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

    protected void waitUntilSharedFilesAreClosed(long timeout) throws Exception {
        Date start = new Date();
        while (injector.getInstance(RangeMappedOpenedFileFactory.class).getSharedFilesCount() != 0) {
            Thread.sleep(100);
            assertThat(new Date().getTime() - start.getTime(), lessThan(timeout));
        }
    }

    protected static InputStream getTestContent() {
        return new ByteArrayInputStream(testContentString.getBytes());
    }
}
