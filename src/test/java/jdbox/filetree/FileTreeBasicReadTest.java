package jdbox.filetree;

import jdbox.driveadapter.File;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.nio.file.Path;

import static jdbox.utils.TestUtils.*;
import static org.hamcrest.MatcherAssert.assertThat;

@Category(FileTree.class)
public class FileTreeBasicReadTest extends BaseFileTreeTest {

    /**
     * List all files, make sure the files are visible.
     */
    @Test
    public void list() throws Exception {

        drive.createFile(getTestFileName(), testFolder, getTestContent());
        File testChildFolder = drive.createFolder(getTestFolderName(), testFolder);
        drive.createFile(getTestFileName(), testChildFolder, getTestContent());

        assertThat(fileTree, contains().defaultTestFile().and().defaultTestFolder());
        assertThat(fileTree, contains().defaultTestFile().in(getTestFolderName()));

        assertCounts(4, 2);
    }

    /**
     * Create a file, make sure it appears.
     */
    @Test
    public void create() throws Exception {
        assertThat(fileTree, contains().nothing());
        drive.createFile(getTestFileName(), testFolder, getTestContent());
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

        testFile.setName(getTestFileName());
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

        Path testChildFolderPath = testDirPath.resolve(getTestFolderName());
        File testChildFolder = drive.createFolder(getTestFolderName(), testFolder);
        createTestFile(testChildFolder);

        fileTree.getChildren(testChildFolderPath);
        assertCounts(3, 2);

        testChildFolder.setName("test_folder_2");
        drive.updateFile(testChildFolder);
        assertThat(fileTree, contains().defaultTestFolder());
        assertThat(fileTree, contains().defaultTestFile().in(testChildFolderPath));

        fileTree.update();
        assertCounts(3, 2);
        assertThat(fileTree, contains().defaultTestFolder().withName("test_folder_2"));
        assertThat(fileTree, contains().defaultTestFile().in("test_folder_2"));
    }
}
