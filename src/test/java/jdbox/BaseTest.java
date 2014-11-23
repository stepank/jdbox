package jdbox;

import com.google.api.services.drive.Drive;
import com.google.inject.Injector;
import org.junit.Before;
import org.junit.BeforeClass;

public class BaseTest {

    private static JdBox.Environment env;
    private static Drive drive;

    protected Injector injector;

    @BeforeClass
    public static void setUpClass() throws Exception {
        env = SetUpTests.createEnvironment();
        drive = JdBox.createDriveService(env);
    }

    @Before
    public void setUp() throws Exception {
        injector = JdBox.createInjector(env, drive, false);
    }
}
