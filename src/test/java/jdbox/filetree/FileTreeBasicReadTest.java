package jdbox.filetree;

import jdbox.JdBox;
import jdbox.driveadapter.File;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.nio.file.Path;

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

        testFile.setName("test_file_2");
        drive.updateFile(testFile);
        assertFileTreeContains().defaultTestFile().only();

        fileTree.update();
        assertFileTreeContains().defaultTestFile().withName("test_file_2").only();

        testFile.setName(testFileName);
        drive.updateFile(testFile);
        assertFileTreeContains().defaultTestFile().withName("test_file_2").only();

        fileTree.update();
        assertFileTreeContains().defaultTestFile().only();

        assertCounts(2, 1);
    }

    /**
     * Rename a directory, make sure it has the new name and its contents are still known.
     */
    @Test
    public void renameDir() throws Exception {

        Path testFolderPath = testDirPath.resolve(testFolderName);
        File testFolder = drive.createFolder(testFolderName, testDir);
        createTestFile(testFolder);

        fileTree.getChildren(testFolderPath);
        assertCounts(3, 2);

        testFolder.setName("test_folder_2");
        drive.updateFile(testFolder);
        assertFileTreeContains().defaultTestFolder().only();
        assertFileTreeContains().in(testFolderPath).defaultTestFile().only();

        fileTree.update();
        assertCounts(3, 2);
        assertFileTreeContains().defaultTestFolder().withName("test_folder_2").only();
        assertFileTreeContains().in(testDirPath.resolve("test_folder_2")).defaultTestFile().only();
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
