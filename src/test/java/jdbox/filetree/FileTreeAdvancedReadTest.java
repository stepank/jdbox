package jdbox.filetree;

import jdbox.driveadapter.File;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;

import static jdbox.filetree.FileTreeMatcher.contains;
import static jdbox.utils.TestUtils.getTestFileName;
import static jdbox.utils.TestUtils.getTestFolderName;
import static org.hamcrest.MatcherAssert.assertThat;

@Category(FileTree.class)
@RunWith(Parameterized.class)
public class FileTreeAdvancedReadTest extends BaseFileTreeTest {

    @Parameterized.Parameter
    public boolean rename;

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{{false}, {true}});
    }

    /**
     * Update a file, make sure it has the new properties.
     */
    @Test
    public void update() throws IOException {

        File testFile = createTestFileAndUpdate();

        if (rename) {
            testFile.setName("test_file_2");
            drive.updateFile(testFile);
        }

        String newContent = "hello beautiful world";
        drive.updateFileContent(testFile, new ByteArrayInputStream(newContent.getBytes()));

        assertThat(fileTree, contains().defaultTestFile());

        fileTree.update();
        assertThat(fileTree, contains()
                .file()
                .withName(rename ? "test_file_2" : getTestFileName())
                .withSize(newContent.length()));

        assertCounts(2, 1);
    }

    /**
     * Trash a file, make sure it disappears.
     */
    @Test
    public void trash() throws IOException {
        File testFile = createTestFileAndUpdate();
        if (rename) {
            testFile.setName("test_file_2");
            drive.updateFile(testFile);
        }
        drive.trashFile(testFile);
        assertThat(fileTree, contains().defaultTestFile());
        fileTree.update();
        assertThat(fileTree, contains().nothing());
        assertCounts(1, 1);
    }

    /**
     * Delete a file, make sure it disappears.
     */
    @Test
    public void delete() throws IOException {
        File testFile = createTestFileAndUpdate();
        if (rename) {
            testFile.setName("test_file_2");
            drive.updateFile(testFile);
        }
        drive.deleteFile(testFile);
        assertThat(fileTree, contains().defaultTestFile());
        fileTree.update();
        assertThat(fileTree, contains().nothing());
        assertCounts(1, 1);
    }

    /**
     * Delete a tracked directory, make sure it disappears.
     */
    @Test
    public void deleteTrackedDir() throws IOException {

        Path folderPath = testDirPath.resolve(getTestFolderName());
        File folder = drive.createFolder(getTestFolderName(), testFolder);
        createTestFileAndUpdate(folder, folderPath);

        assertCounts(3, 2);

        if (rename) {
            folder.setName(getTestFolderName() + "_2");
            drive.updateFile(folder);
        }
        drive.deleteFile(folder);
        assertThat(fileTree, contains().defaultTestFolder());

        fileTree.update();
        assertThat(fileTree, contains().nothing());

        assertCounts(1, 1);
    }

    /**
     * Move a file from one directory into another, make sure the file disappears from one directory and appears in the other.
     */
    @Test
    public void move() throws IOException {

        Path sourcePath = testDirPath.resolve("source");
        File source = drive.createFolder("source", testFolder);
        File testFile = createTestFile(source);

        Path destinationPath = testDirPath.resolve("destination");
        File destination = drive.createFolder("destination", testFolder);

        fileTree.getChildren(sourcePath);
        fileTree.getChildren(destinationPath);

        if (rename)
            testFile.setName("test_file_2");
        testFile.setParentId(destination.getId());
        drive.updateFile(testFile);
        assertThat(fileTree, contains().defaultTestFile().in(sourcePath));
        assertThat(fileTree, contains().nothing().in(destinationPath));

        fileTree.update();
        assertThat(fileTree, contains().nothing().in(sourcePath));
        assertThat(fileTree, contains()
                .defaultTestFile().withName(rename ? "test_file_2" : getTestFileName()).in(destinationPath));

        assertCounts(4, 3);
    }

    /**
     * Move a file from one directory into another one that is not tracked, make sure the file disappears.
     */
    @Test
    public void moveToNotTrackedDir() throws IOException {

        Path sourcePath = testDirPath.resolve("source");
        File source = drive.createFolder("source", testFolder);
        File testFile = createTestFile(source);

        File destination = drive.createFolder("destination", testFolder);

        fileTree.getChildren(sourcePath);

        if (rename)
            testFile.setName("test_file_2");
        testFile.setParentId(destination.getId());
        drive.updateFile(testFile);
        assertThat(fileTree, contains().defaultTestFile().in(sourcePath));

        fileTree.update();
        assertThat(fileTree, contains().nothing().in(sourcePath));

        assertCounts(3, 2);
    }

    /**
     * Move a file from one directory that is not tracked into another one, make sure the file appears.
     */
    @Test
    public void moveFromNotTrackedDir() throws IOException {

        File source = drive.createFolder("source", testFolder);
        File testFile = createTestFile(source);

        Path destinationPath = testDirPath.resolve("destination");
        File destination = drive.createFolder("destination", testFolder);

        fileTree.getChildren(destinationPath);

        if (rename)
            testFile.setName("test_file_2");
        testFile.setParentId(destination.getId());
        drive.updateFile(testFile);
        assertThat(fileTree, contains().nothing().in(destinationPath));

        fileTree.update();
        assertThat(fileTree, contains()
                .defaultTestFile()
                .withName(rename ? "test_file_2" : getTestFileName())
                .in(destinationPath));

        assertCounts(4, 2);
    }
}
