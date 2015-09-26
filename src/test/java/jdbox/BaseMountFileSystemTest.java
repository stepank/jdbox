package jdbox;

import jdbox.filetree.FileTree;
import jdbox.utils.MountedFileSystem;
import jdbox.utils.OrderedRule;
import org.junit.After;
import org.junit.Before;

import java.nio.file.Path;

public class BaseMountFileSystemTest extends BaseFileSystemModuleTest {

    @OrderedRule
    public final MountedFileSystem fileSystem = new MountedFileSystem(errorCollector, lifeCycleManager);

    protected Path mountPoint;

    @Before
    public void setUp() {
        super.setUp();
        mountPoint = fileSystem.getMountPoint();
    }

    @After
    public void tearDown() throws InterruptedException {
        lifeCycleManager.waitUntilLocalStorageIsEmpty();
    }

    protected void resetFileTree() throws InterruptedException {
        lifeCycleManager.getInstance(FileTree.class).reset();
    }
}
