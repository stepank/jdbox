package jdbox.filetree;

import jdbox.JdBox;
import jdbox.driveadapter.File;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.nio.file.Path;

import static org.hamcrest.MatcherAssert.assertThat;

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

        assertThat(fileTree, contains().defaultTestFile().and().defaultTestFolder());
        assertThat(fileTree, contains().defaultTestFile().in(testFolderName));

        assertCounts(4, 2);
    }

    /**
     * Create a file, make sure it appears.
     */
    @Test
    public void create() throws Exception {
        assertThat(fileTree, contains().nothing());
        drive.createFile(testFileName, testDir, getTestContent());
        assertThat(fileTree, contains().nothing());
        fileTree.update();
        assertThat(fileTree, contains().defaultTestFile());
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
        assertThat(fileTree, contains().defaultTestFile());

        fileTree.update();
        assertThat(fileTree, contains().defaultTestFile().withName("test_file_2"));

        testFile.setName(testFileName);
        drive.updateFile(testFile);
        assertThat(fileTree, contains().defaultTestFile().withName("test_file_2"));

        fileTree.update();
        assertThat(fileTree, contains().defaultTestFile());

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
        assertThat(fileTree, contains().defaultTestFolder());
        assertThat(fileTree, contains().defaultTestFile().in(testFolderPath));

        fileTree.update();
        assertCounts(3, 2);
        assertThat(fileTree, contains().defaultTestFolder().withName("test_folder_2"));
        assertThat(fileTree, contains().defaultTestFile().in("test_folder_2"));
    }

    /**
     * List all files, make sure that a typed file w/o extension is listed with extension.
     */
    @Test
    public void extensionIsAdded() throws Exception {

        assertThat(fileTree, contains().nothing());

        drive.createFile(testFileName, testDir, JdBox.class.getResource("/test.pdf").openStream());

        assertThat(fileTree, contains().nothing());

        fileTree.update();

        assertThat(fileTree, contains()
                .file()
                .withName(testFileName + ".pdf")
                .withRealName(testFileName));

        assertCounts(2, 1);
    }

    /**
     * List all files, make sure that a typed file with extension is listed with extension.
     */
    @Test
    public void extensionIsPreserved() throws Exception {

        assertThat(fileTree, contains().nothing());

        drive.createFile(testFileName + ".pdf", testDir, JdBox.class.getResource("/test.pdf").openStream());

        assertThat(fileTree, contains().nothing());

        fileTree.update();

        assertThat(fileTree, contains().file().withName(testFileName + ".pdf"));

        assertCounts(2, 1);
    }

    /**
     * List all files, make sure that a typed file w/o extension is listed w/o extension
     * (because there is already a file with this name).
     */
    @Test
    public void extensionIsNotAdded() throws Exception {

        assertThat(fileTree, contains().nothing());

        drive.createFile(testFileName + ".pdf", testDir, getTestContent());
        drive.createFile(testFileName, testDir, JdBox.class.getResource("/test.pdf").openStream());

        assertThat(fileTree, contains().nothing());

        fileTree.update();

        assertThat(fileTree, contains()
                .file().withName(testFileName).and()
                .file().defaultTestFile().withName(testFileName + ".pdf"));

        assertCounts(3, 1);
    }
}
