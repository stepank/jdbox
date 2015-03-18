package jdbox;

import jdbox.filetree.File;
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
        String actual = new String(Files.readAllBytes(mountPoint.resolve(testDir.getName()).resolve("test.txt")));
        assertThat(actual, equalTo(testContentString));
    }

    @Test
    public void writeAndRead() throws Exception {
        Files.write(mountPoint.resolve(testDir.getName()).resolve("test.txt"), testContentString.getBytes());
        String actual = new String(Files.readAllBytes(mountPoint.resolve(testDir.getName()).resolve("test.txt")));
        assertThat(actual, equalTo(testContentString));
    }

    @Test
    public void writeAndReadAfterClose() throws Exception {
        Files.write(mountPoint.resolve(testDir.getName()).resolve("test.txt"), testContentString.getBytes());
        waitUntilSharedFilesAreClosed();
        String actual = new String(Files.readAllBytes(mountPoint.resolve(testDir.getName()).resolve("test.txt")));
        assertThat(actual, equalTo(testContentString));
    }

    @Test
    public void writeAndTrackSize() throws Exception {

        Path path = mountPoint.resolve(testDir.getName()).resolve("test.txt");

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
        Path path = mountPoint.resolve(testDir.getName()).resolve("test.txt");
        Files.write(path, testContentString.getBytes());
        waitUntilSharedFilesAreClosed();
        new FileOutputStream(path.toFile(), true).getChannel().truncate(5).close();
        assertThat(new String(Files.readAllBytes(path)), equalTo(testContentString.substring(0, 5)));
    }

    @Test
    public void remove() throws Exception {

        File folder = drive.createFolder("test", testDir);
        drive.createFile("test.txt", folder, getTestContent());

        Path dirPath = mountPoint.resolve(testDir.getName()).resolve("test");
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
}