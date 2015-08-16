package jdbox;

import jdbox.filetree.FileTree;
import jdbox.utils.MountedFileSystem;
import jdbox.utils.OrderedRule;
import jdbox.utils.TestUtils;
import org.junit.After;
import org.junit.Before;

import java.nio.file.Path;

public class BaseMountFileSystemTest extends BaseFileSystemModuleTest {

    @OrderedRule
    public MountedFileSystem fileSystem = new MountedFileSystem(errorCollector, injectorProvider);

    protected Path mountPoint;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        mountPoint = fileSystem.getMountPoint();
    }

    @After
    public void tearDown() throws Exception {
        TestUtils.waitUntilLocalStorageIsEmpty(injectorProvider.getInjector());
    }

    protected void resetFileTree() throws InterruptedException {
        injectorProvider.getInjector().getInstance(FileTree.class).reset();
    }
}
