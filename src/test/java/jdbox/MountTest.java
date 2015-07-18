package jdbox;

import jdbox.driveadapter.File;
import org.junit.Test;

import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

public class MountTest extends BaseMountFileSystemTest {

    @Test
    public void read() throws Exception {
        drive.createFile("test.txt", testDir, getTestContent());
        resetFileTree();
        String actual = new String(Files.readAllBytes(mountPoint.resolve("test.txt")));
        assertThat(actual, equalTo(testContentString));
    }

    @Test
    public void writeAndRead() throws Exception {
        Files.write(mountPoint.resolve("test.txt"), testContentString.getBytes());
        String actual = new String(Files.readAllBytes(mountPoint.resolve("test.txt")));
        assertThat(actual, equalTo(testContentString));
    }

    @Test
    public void writeAndReadAfterClose() throws Exception {
        Files.write(mountPoint.resolve("test.txt"), testContentString.getBytes());
        waitUntilLocalStorageIsEmpty();
        String actual = new String(Files.readAllBytes(mountPoint.resolve("test.txt")));
        assertThat(actual, equalTo(testContentString));
    }

    @Test
    public void writeAndTrackSize() throws Exception {

        Path path = mountPoint.resolve("test.txt");

        try (OutputStream stream = Files.newOutputStream(path)) {
            stream.write(testContentString.substring(0, 6).getBytes());
            assertThat(path.toFile().length(), equalTo((long) 6));
            stream.write(testContentString.substring(6).getBytes());
            assertThat(path.toFile().length(), equalTo((long) testContentString.length()));
        }

        assertThat(path.toFile().length(), equalTo((long) testContentString.length()));
    }

    @Test
    public void truncate() throws Exception {
        Path path = mountPoint.resolve("test.txt");
        Files.write(path, testContentString.getBytes());
        waitUntilLocalStorageIsEmpty();
        new FileOutputStream(path.toFile(), true).getChannel().truncate(5).close();
        assertThat(new String(Files.readAllBytes(path)), equalTo(testContentString.substring(0, 5)));
    }

    @Test
    public void remove() throws Exception {

        File folder = drive.createFolder("test", testDir);
        drive.createFile("test.txt", folder, getTestContent());

        resetFileTree();

        Path dirPath = mountPoint.resolve("test");
        Path filePath = dirPath.resolve("test.txt");

        assertThat(Files.exists(filePath), is(true));

        try {
            Files.delete(dirPath);
            throw new AssertionError("non-empty directory must not have been deleted");
        } catch (DirectoryNotEmptyException ignored) {
        }

        Files.delete(filePath);
        waitUntilUploaderIsDone();
        assertThat(Files.exists(filePath), is(false));

        Files.delete(dirPath);
        waitUntilUploaderIsDone();
        assertThat(Files.exists(dirPath), is(false));
    }

    @Test
    public void move() throws Exception {

        File source = drive.createFolder("source", testDir);
        drive.createFolder("destination", testDir);
        drive.createFile("test.txt", source, getTestContent());

        resetFileTree();

        Path sourcePath = mountPoint.resolve("source").resolve("test.txt");
        Path destinationPath = mountPoint.resolve("destination").resolve("test_2.txt");

        assertThat(Files.exists(sourcePath), is(true));
        assertThat(Files.exists(destinationPath), is(false));

        Files.move(sourcePath, destinationPath);
        waitUntilUploaderIsDone();
        assertThat(Files.exists(sourcePath), is(false));
        assertThat(Files.exists(destinationPath), is(true));
    }
}