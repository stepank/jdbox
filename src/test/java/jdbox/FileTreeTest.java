package jdbox;

import jdbox.filetree.File;
import jdbox.filetree.FileTree;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class FileTreeTest extends BaseTest {

    private final static String testFolderName = "test_folder";
    private final static String testFileName = "test_file";
    private final static String testContentString = "hello world";

    private DriveAdapter drive;
    private FileTree fileTree;
    private Path testDirPath;
    private File testDir;

    @Before
    public void setUp() throws Exception {

        super.setUp();

        drive = injector.getInstance(DriveAdapter.class);
        fileTree = injector.getInstance(FileTree.class);

        String testDirName = UUID.randomUUID().toString();
        testDirPath = Paths.get("/", testDirName);
        testDir = drive.createFolder(testDirName, fileTree.getRoot());
    }

    @After
    public void tearDown() throws Exception {
        drive.deleteFile(testDir);
    }

    /**
     * List all files, make sure the files are visible.
     */
    @Test
    public void list() throws Exception {

        drive.createFile(testFileName, testDir, getTestContent());
        File testFolder = drive.createFolder(testFolderName, testDir);
        drive.createFile(testFileName, testFolder, getTestContent());

        Map<String, File> children = assertTestDirContainsTestFile();
        assertThat(children.size(), equalTo(2));
        assertThat(children.get(testFolderName).getName(), equalTo(testFolderName));
        assertThat(children.get(testFolderName).getSize(), equalTo((long) 0));
        assertThat(children.get(testFolderName).isDirectory(), equalTo(true));

        assertTestDirContainsOnlyTestFile(testDirPath.resolve(testFolderName));
    }

    /**
     * Create a file, make sure the file appears.
     */
    @Test
    public void create() throws Exception {
        assertTestDirContainsNothing();
        drive.createFile(testFileName, testDir, getTestContent());
        assertTestDirContainsNothing();
        fileTree.update();
        assertTestDirContainsOnlyTestFile();
    }

    /**
     * Trash a file, make sure it is gone.
     */
    @Test
    public void trash() throws Exception {
        File testFile = createTestFileAndUpdate();
        drive.trashFile(testFile);
        assertTestDirContainsOnlyTestFile();
        fileTree.update();
        assertTestDirContainsNothing();
    }

    /**
     * Delete a file, make sure it is gone.
     */
    @Test
    public void delete() throws Exception {
        File testFile = createTestFileAndUpdate();
        drive.deleteFile(testFile);
        assertTestDirContainsOnlyTestFile();
        fileTree.update();
        assertTestDirContainsNothing();
    }

    /**
     * Rename a file, make sure it has the new name.
     */
    @Test
    public void rename() throws Exception {
        File testFile = createTestFileAndUpdate();
        drive.renameFile(testFile, "test_file_2");
        assertTestDirContainsOnlyTestFile();
        fileTree.update();
        assertTestDirContainsOnlyTestFile("test_file_2");
    }

    /**
     * Rename and trash a file, make sure it disappears.
     */
    @Test
    public void renameAndTrash() throws Exception {
        File testFile = createTestFileAndUpdate();
        drive.renameFile(testFile, "test_file_2");
        drive.trashFile(drive.getChildren(testDir).get(0));
        assertTestDirContainsOnlyTestFile();
        fileTree.update();
        assertTestDirContainsNothing();
    }

    /**
     * Rename and delete a file, make sure it disappears.
     */
    @Test
    public void renameAndDelete() throws Exception {
        File testFile = createTestFileAndUpdate();
        drive.renameFile(testFile, "test_file_2");
        drive.deleteFile(drive.getChildren(testDir).get(0));
        assertTestDirContainsOnlyTestFile();
        fileTree.update();
        assertTestDirContainsNothing();
    }

    /**
     * Move a file from one directory into another one, make sure the file disappears from one directory and appears in the other.
     */
    @Test
    public void move() throws Exception {

        Path sourcePath = testDirPath.resolve("source");
        File source = drive.createFolder("source", testDir);
        File testFile = createTestFile(source);

        Path destinationPath = testDirPath.resolve("destination");
        File destination = drive.createFolder("destination", testDir);

        fileTree.getChildren(sourcePath);
        fileTree.getChildren(destinationPath);

        drive.moveFile(testFile, destination);
        assertTestDirContainsOnlyTestFile(sourcePath, testFileName);
        assertTestDirContainsNothing(destinationPath);

        fileTree.update();
        assertTestDirContainsNothing(sourcePath);
        assertTestDirContainsOnlyTestFile(destinationPath, testFileName);
    }

    private File createTestFile(File parent) throws Exception {
        return drive.createFile(testFileName, parent, getTestContent());
    }

    private File createTestFileAndUpdate() throws Exception {
        return createTestFileAndUpdate(testDir, testDirPath);
    }

    private File createTestFileAndUpdate(File parent, Path parentPath) throws Exception {
        createTestFile(parent);
        return fileTree.getChildren(parentPath).get(testFileName);
    }

    private void assertTestDirContainsNothing() throws Exception {
        assertTestDirContainsNothing(testDirPath);
    }

    private void assertTestDirContainsNothing(Path path) throws Exception {
        assertThat(fileTree.getChildren(path).size(), equalTo(0));
    }

    private void assertTestDirContainsOnlyTestFile() throws Exception {
        assertTestDirContainsOnlyTestFile(testDirPath);
    }

    private void assertTestDirContainsOnlyTestFile(Path path) throws Exception {
        assertTestDirContainsOnlyTestFile(path, testFileName);
    }

    private void assertTestDirContainsOnlyTestFile(String name) throws Exception {
        assertTestDirContainsOnlyTestFile(testDirPath, name);
    }

    private void assertTestDirContainsOnlyTestFile(Path path, String name) throws Exception {
        Map<String, File> children = fileTree.getChildren(path);
        assertThat(children.size(), equalTo(1));
        assertContainsTestFile(children, name);
    }

    private Map<String, File> assertTestDirContainsTestFile() throws Exception {
        return assertTestDirContainsTestFile(testFileName);
    }

    private Map<String, File> assertTestDirContainsTestFile(String name) throws Exception {
        Map<String, File> children = fileTree.getChildren(testDirPath);
        assertContainsTestFile(children, name);
        return children;
    }

    private static void assertContainsTestFile(Map<String, File> children, String name) {
        assertThat(children.get(name).getName(), equalTo(name));
        assertThat(children.get(name).getSize(), equalTo((long) testContentString.length()));
        assertThat(children.get(name).isDirectory(), equalTo(false));
    }

    private static InputStream getTestContent() {
        return new ByteArrayInputStream(testContentString.getBytes());
    }
}
