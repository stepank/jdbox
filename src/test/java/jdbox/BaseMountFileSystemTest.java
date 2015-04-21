package jdbox;

import org.ini4j.Ini;
import org.junit.After;
import org.junit.Before;

import java.nio.file.Path;
import java.nio.file.Paths;

public class BaseMountFileSystemTest extends BaseFileSystemTest {

    protected static Path mountPoint;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        mountPoint = Paths.get(injector.getInstance(Ini.class).get("Main", "mount_point"));
        fs.mount(new java.io.File(mountPoint.toString()), false);
    }

    @After
    public void tearDown() throws Exception {
        try {
            waitUntilLocalStorageIsEmpty();
            fs.unmount();
        } finally {
            super.tearDown();
        }
    }
}
