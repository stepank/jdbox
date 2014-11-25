package jdbox.filetree;

import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.nio.file.Path;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

@Category(FileTree.class)
public class FileTreeBasicTest extends BaseFileTreeTest {

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

        assertCounts(3, 2);
    }

    /**
     * Create a file, make sure it appears.
     */
    @Test
    public void create() throws Exception {
        assertTestDirContainsNothing();
        drive.createFile(testFileName, testDir, getTestContent());
        assertTestDirContainsNothing();
        fileTree.update();
        assertTestDirContainsOnlyTestFile();
        assertCounts(1, 1);
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
        assertCounts(1, 1);
    }
}
