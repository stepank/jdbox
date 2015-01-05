package jdbox.filetree;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@Category(FileTree.class)
public class FileTreeBasicReadTest extends BaseFileTreeTest {

    /**
     * List all files, make sure the files are visible.
     */
    @Test
    public void list() throws Exception {

        drive.createFile(testFileName, testDir, getTestContent());
        File testFolder = drive.createFolder(testFolderName, testDir);
        drive.createFile(testFileName, testFolder, getTestContent());

        assertFileTreeContains().defaultTestFile().and().defaultTestFolder().check();
        assertFileTreeContains().in(testFolderName).defaultTestFile().only();

        assertCounts(3, 2);
    }

    /**
     * Create a file, make sure it appears.
     */
    @Test
    public void create() throws Exception {
        assertFileTreeContains().nothing();
        drive.createFile(testFileName, testDir, getTestContent());
        assertFileTreeContains().nothing();
        fileTree.update();
        assertFileTreeContains().defaultTestFile().only();
        assertCounts(1, 1);
    }

    /**
     * Rename a file, make sure it has the new name.
     */
    @Test
    public void rename() throws Exception {
        File testFile = createTestFileAndUpdate();
        drive.renameFile(testFile, "test_file_2");
        assertFileTreeContains().defaultTestFile().only();
        fileTree.update();
        assertFileTreeContains().defaultTestFile().withName("test_file_2").only();
        assertCounts(1, 1);
    }
}
