package jdbox.filetree;

import com.google.inject.Module;
import jdbox.BaseLifeCycleManagerTest;
import jdbox.driveadapter.DriveAdapter;
import jdbox.driveadapter.File;
import jdbox.localstate.LocalStateModule;
import jdbox.uploader.UploaderModule;
import jdbox.utils.TestUtils;
import jdbox.utils.driveadapter.MockDriveAdapterModule;
import jdbox.utils.driveadapter.Unsafe;
import jdbox.utils.driveadapter.UnsafeDriveAdapterModule;
import org.junit.Before;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class BaseFileTreeTest extends BaseLifeCycleManagerTest {

    protected final static Path testDirPath = Paths.get("/");

    protected DriveAdapter drive;
    protected FileTree fileTree;
    protected File testFolder;

    @Override
    protected List<Module> getRequiredModules() {
        return new ArrayList<Module>() {{
            add(new MockDriveAdapterModule(
                    driveServiceProvider.getDriveService(), testFolderProvider.getBasicInfoProvider()));
            add(new UnsafeDriveAdapterModule());
            add(new UploaderModule());
            add(new LocalStateModule());
            add(new TestFileTreeModule(false));
        }};
    }

    @Before
    public void setUp() throws IOException {
        drive = lifeCycleManager.getInstance(DriveAdapter.class, Unsafe.class);
        fileTree = lifeCycleManager.getInstance(FileTree.class);
        testFolder = testFolderProvider.getOrCreate();
    }

    protected File createTestFile(File parent) throws IOException {
        return drive.createFile(TestUtils.testFileName, parent, TestUtils.getTestContent());
    }

    protected File createTestFileAndUpdate() throws IOException {
        return createTestFileAndUpdate(testFolderProvider.getOrCreate(), Paths.get("/"));
    }

    protected File createTestFileAndUpdate(File parent, Path parentPath) throws IOException {
        File file = createTestFile(parent);
        fileTree.getChildren(parentPath);
        return file;
    }

    protected void assertCounts(int knownFileCount, int trackedDirCount) {
        assertCounts(fileTree, knownFileCount, trackedDirCount);
    }

    protected void assertCounts(FileTree fileTree, int knownFileCount, int trackedDirCount) {
        assertThat(fileTree.getKnownFileCount(), equalTo(knownFileCount));
        assertThat(fileTree.getTrackedDirCount(), equalTo(trackedDirCount));
    }
}
