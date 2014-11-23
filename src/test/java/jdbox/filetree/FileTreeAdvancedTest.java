package jdbox.filetree;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

@RunWith(Parameterized.class)
public class FileTreeAdvancedTest extends BaseFileTreeTest {

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

        Date newDate = new Date(new Date().getTime() + 3600);
        drive.touchFile(testFile, newDate);

        assertTestDirContainsOnlyTestFile();

        fileTree.update();

        String fileName = rename ? "test_file_2" : testFileName;
        Map<String, File> children = fileTree.getChildren(testDirPath);
        assertThat(children.get(fileName).getName(), equalTo(fileName));
        assertThat(children.get(fileName).isDirectory(), equalTo(false));
        assertThat(children.get(fileName).getAccessedDate(), equalTo(newDate));
        assertThat(children.get(fileName).getSize(), equalTo((long) newContent.length()));
    }

    /**
     * Trash a file, make sure it disappears.
     */
    @Test
    public void trash() throws Exception {
        File testFile = createTestFileAndUpdate();
        if (rename)
            drive.renameFile(testFile, "test_file_2");
        drive.trashFile(drive.getChildren(testDir).get(0));
        assertTestDirContainsOnlyTestFile();
        fileTree.update();
        assertTestDirContainsNothing();
    }

    /**
     * Delete a file, make sure it disappears.
     */
    @Test
    public void delete() throws Exception {
        File testFile = createTestFileAndUpdate();
        if (rename)
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

        if (rename)
            drive.renameFile(testFile, "test_file_2");
        drive.moveFile(testFile, destination);
        assertTestDirContainsOnlyTestFile(sourcePath, testFileName);
        assertTestDirContainsNothing(destinationPath);

        fileTree.update();
        assertTestDirContainsNothing(sourcePath);
        assertTestDirContainsOnlyTestFile(destinationPath, rename ? "test_file_2" : testFileName);
    }
}
