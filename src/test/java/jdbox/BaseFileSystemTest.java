package jdbox;

import com.google.api.services.drive.Drive;
import com.google.inject.Inject;
import com.google.inject.Injector;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;

public class BaseFileSystemTest {

    protected static Injector injector;

    protected FileSystem fs;

    @BeforeClass
    public static void setUpClass() throws Exception {
        injector = SetUpForTests.createInjector();
        injector.getInstance(Drive.class);
    }

    @Before
    public void setUp() throws Exception {
        fs = injector.getInstance(FileSystem.class);
    }
}
