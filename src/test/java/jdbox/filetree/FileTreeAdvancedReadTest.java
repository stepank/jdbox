package jdbox.filetree;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;

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
    public void update() throws Exception {

        File testFile = createTestFileAndUpdate();

        if (rename)
            drive.renameFile(testFile, "test_file_2");

        String newContent = "hello beautiful world";
        drive.updateFileContent(testFile, new ByteArrayInputStream(newContent.getBytes()));

        assertFileTreeContains().defaultTestFile().only();

        fileTree.update();
        assertFileTreeContains()
                .file()
                .withName(rename ? "test_file_2" : testFileName)
                .withSize(newContent.length()).only();

        assertCounts(2, 1);
    }

    /**
     * Trash a file, make sure it disappears.
     */
    @Test
    public void trash() throws Exception {
        File testFile = createTestFileAndUpdate();
        if (rename)
            drive.renameFile(testFile, "test_file_2");
        drive.trashFile(testFile);
        assertFileTreeContains().defaultTestFile().only();
        fileTree.update();
        assertFileTreeContains().nothing();
        assertCounts(1, 1);
    }

    /**
     * Delete a file, make sure it disappears.
     */
    @Test
    public void delete() throws Exception {
        File testFile = createTestFileAndUpdate();
        if (rename)
            drive.renameFile(testFile, "test_file_2");
        drive.deleteFile(testFile);
        assertFileTreeContains().defaultTestFile().only();
        fileTree.update();
        assertFileTreeContains().nothing();
        assertCounts(1, 1);
    }

    /**
     * Delete a tracked directory, make sure it disappears.
     */
    @Test
    public void deleteTrackedDir() throws Exception {

        Path folderPath = testDirPath.resolve(testFolderName);
        File folder = drive.createFolder(testFolderName, testDir);
        createTestFileAndUpdate(folder, folderPath);

        assertCounts(3, 2);

        if (rename)
            drive.renameFile(folder, testFolderName + "_2");
        drive.deleteFile(folder);
        assertFileTreeContains().defaultTestFolder().only();

        fileTree.update();
        assertFileTreeContains().nothing();

        assertCounts(1, 1);
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

        if (rename)
            drive.renameFile(testFile, "test_file_2");
        drive.moveFile(testFile, destination);
        assertFileTreeContains().in(sourcePath).defaultTestFile().only();
        assertFileTreeContains().in(destinationPath).nothing();

        fileTree.update();
        assertFileTreeContains().in(sourcePath).nothing();
        assertFileTreeContains()
                .in(destinationPath).defaultTestFile().withName(rename ? "test_file_2" : testFileName).only();

        assertCounts(4, 3);
    }

    /**
     * Move a file from one directory into another one that is not tracked, make sure the file disappears.
     */
    @Test
    public void moveToNotTrackedDir() throws Exception {

        Path sourcePath = testDirPath.resolve("source");
        File source = drive.createFolder("source", testDir);
        File testFile = createTestFile(source);

        File destination = drive.createFolder("destination", testDir);

        fileTree.getChildren(sourcePath);

        if (rename)
            drive.renameFile(testFile, "test_file_2");
        drive.moveFile(testFile, destination);
        assertFileTreeContains().in(sourcePath).defaultTestFile().only();

        fileTree.update();
        assertFileTreeContains().in(sourcePath).nothing();

        assertCounts(3, 2);
    }

    /**
     * Move a file from one directory that is not tracked into another one, make sure the file appears.
     */
    @Test
    public void moveFromNotTrackedDir() throws Exception {

        File source = drive.createFolder("source", testDir);
        File testFile = createTestFile(source);

        Path destinationPath = testDirPath.resolve("destination");
        File destination = drive.createFolder("destination", testDir);

        fileTree.getChildren(destinationPath);

        if (rename)
            drive.renameFile(testFile, "test_file_2");
        drive.moveFile(testFile, destination);
        assertFileTreeContains().in(destinationPath).nothing();

        fileTree.update();
        assertFileTreeContains().in(destinationPath).defaultTestFile().withName(rename ? "test_file_2" : testFileName).only();

        assertCounts(4, 2);
    }
}
