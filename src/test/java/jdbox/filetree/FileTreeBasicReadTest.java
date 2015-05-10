package jdbox.filetree;

import jdbox.JdBox;
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

        assertFileTreeContains().defaultTestFile().and().defaultTestFolder().only();
        assertFileTreeContains().in(testFolderName).defaultTestFile().only();

        assertCounts(4, 2);
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
        assertCounts(2, 1);
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

        drive.renameFile(testFile, testFileName);
        assertFileTreeContains().defaultTestFile().withName("test_file_2").only();

        fileTree.update();
        assertFileTreeContains().defaultTestFile().only();

        assertCounts(2, 1);
    }

    /**
     * List all files, make sure that a typed file w/o extension is listed with extension.
     */
    @Test
    public void extensionIsAdded() throws Exception {

        assertFileTreeContains().nothing();

        drive.createFile(testFileName, testDir, JdBox.class.getResource("/test.pdf").openStream());

        assertFileTreeContains().nothing();

        fileTree.update();

        assertFileTreeContains().file()
                .withName(testFileName + ".pdf")
                .withRealName(testFileName)
                .only();

        assertCounts(2, 1);
    }

    /**
     * List all files, make sure that a typed file with extension is listed with extension.
     */
    @Test
    public void extensionIsPreserved() throws Exception {

        assertFileTreeContains().nothing();

        drive.createFile(testFileName + ".pdf", testDir, JdBox.class.getResource("/test.pdf").openStream());

        assertFileTreeContains().nothing();

        fileTree.update();

        assertFileTreeContains().file().withName(testFileName + ".pdf").only();

        assertCounts(2, 1);
    }

    /**
     * List all files, make sure that a typed file w/o extension is listed w/o extension
     * (because there is already a file with this name).
     */
    @Test
    public void extensionIsNotAdded() throws Exception {

        assertFileTreeContains().nothing();

        drive.createFile(testFileName + ".pdf", testDir, getTestContent());
        drive.createFile(testFileName, testDir, JdBox.class.getResource("/test.pdf").openStream());

        assertFileTreeContains().nothing();

        fileTree.update();

        assertFileTreeContains()
                .file().withName(testFileName).and()
                .file().defaultTestFile().withName(testFileName + ".pdf").only();

        assertCounts(3, 1);
    }
}
