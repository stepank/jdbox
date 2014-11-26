package jdbox.filetree;

import jdbox.BaseTest;
import jdbox.DriveAdapter;
import org.junit.After;
import org.junit.Before;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class BaseFileTreeTest extends BaseTest {

    protected final static String testFolderName = "test_folder";
    protected final static String testFileName = "test_file";
    protected final static String testContentString = "hello world";

    protected DriveAdapter drive;
    protected FileTree fileTree;
    protected Path testDirPath;
    protected File testDir;

    @Before
    public void setUp() throws Exception {

        super.setUp();

        drive = injector.getInstance(DriveAdapter.class);
        fileTree = injector.getInstance(FileTree.class);

        String testDirName = UUID.randomUUID().toString();
        testDirPath = Paths.get("/");
        testDir = drive.createFolder(testDirName, fileTree.getRoot());

        fileTree.setRoot(testDir);
    }

    @After
    public void tearDown() throws Exception {
        drive.deleteFile(testDir);
    }

    protected File createTestFile(File parent) throws Exception {
        return drive.createFile(testFileName, parent, getTestContent());
    }

    protected File createTestFileAndUpdate() throws Exception {
        return createTestFileAndUpdate(testDir, testDirPath);
    }

    protected File createTestFileAndUpdate(File parent, Path parentPath) throws Exception {
        createTestFile(parent);
        return fileTree.getChildren(parentPath).get(testFileName);
    }

    protected void assertTestDirContainsNothing() throws Exception {
        assertTestDirContainsNothing(testDirPath);
    }

    protected void assertTestDirContainsNothing(Path path) throws Exception {
        assertThat(fileTree.getChildren(path).size(), equalTo(0));
    }

    protected void assertTestDirContainsOnlyTestFile() throws Exception {
        assertTestDirContainsOnlyTestFile(testDirPath);
    }

    protected void assertTestDirContainsOnlyTestFile(Path path) throws Exception {
        assertTestDirContainsOnlyTestFile(path, testFileName);
    }

    protected void assertTestDirContainsOnlyTestFile(String name) throws Exception {
        assertTestDirContainsOnlyTestFile(testDirPath, name);
    }

    protected void assertTestDirContainsOnlyTestFile(Path path, String name) throws Exception {
        Map<String, File> children = fileTree.getChildren(path);
        assertThat(children.size(), equalTo(1));
        assertContainsTestFile(children, name);
    }

    protected Map<String, File> assertTestDirContainsTestFile() throws Exception {
        return assertTestDirContainsTestFile(testFileName);
    }

    protected Map<String, File> assertTestDirContainsTestFile(String name) throws Exception {
        Map<String, File> children = fileTree.getChildren(testDirPath);
        assertContainsTestFile(children, name);
        return children;
    }

    protected void assertCounts(int knownFilesCount, int trackedDirsCount) {
        assertThat(fileTree.getKnownFilesCount(), equalTo(knownFilesCount));
        assertThat(fileTree.getTrackedDirsCount(), equalTo(trackedDirsCount));
    }

    protected static void assertContainsTestFile(Map<String, File> children, String name) {
        assertThat(children.get(name).getName(), equalTo(name));
        assertThat(children.get(name).getSize(), equalTo((long) testContentString.length()));
        assertThat(children.get(name).isDirectory(), equalTo(false));
    }

    protected static InputStream getTestContent() {
        return new ByteArrayInputStream(testContentString.getBytes());
    }
}
