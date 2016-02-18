package jdbox;

import jdbox.localstate.LocalState;
import jdbox.utils.MountedFileSystem;
import jdbox.utils.OrderedRule;
import org.junit.After;
import org.junit.Before;

import java.io.IOException;
import java.nio.file.Path;

public class BaseMountFileSystemTest extends BaseFileSystemModuleTest {

    @OrderedRule
    public final MountedFileSystem fileSystem =
            new MountedFileSystem(errorCollector, tempFolderProvider, lifeCycleManager);

    protected Path mountPoint;

    @Before
    public void setUp() throws IOException {
        super.setUp();
        mountPoint = fileSystem.getMountPoint();
    }

    @After
    public void tearDown() throws InterruptedException {
        lifeCycleManager.waitUntilLocalStorageIsEmpty();
    }

    protected void resetLocalState() throws InterruptedException {
        lifeCycleManager.getInstance(LocalState.class).reset();
    }
}
