package jdbox;

import com.google.api.services.drive.Drive;
import com.google.inject.Injector;
import org.junit.Before;
import org.junit.BeforeClass;

public class BaseFileSystemTest extends BaseTest {

    protected FileSystem fs;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        fs = injector.getInstance(FileSystem.class);
    }
}
