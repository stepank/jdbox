package jdbox;

import jdbox.driveadapter.File;
import org.junit.Test;

import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static jdbox.utils.TestUtils.getTestContent;
import static jdbox.utils.TestUtils.getTestContentBytes;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

public class MountTest extends BaseMountFileSystemTest {

    @Test
    public void read() throws Exception {
        drive.createFile("test.txt", testFolder, getTestContent());
        resetFileTree();
        assertThat(Files.readAllBytes(mountPoint.resolve("test.txt")), equalTo(getTestContentBytes()));
    }

    @Test
    public void writeAndRead() throws Exception {
        Files.write(mountPoint.resolve("test.txt"), getTestContentBytes());
        assertThat(Files.readAllBytes(mountPoint.resolve("test.txt")), equalTo(getTestContentBytes()));
    }

    @Test
    public void writeAndReadAfterClose() throws Exception {
        Files.write(mountPoint.resolve("test.txt"), getTestContentBytes());
        lifeCycleManager.waitUntilLocalStorageIsEmpty();
        assertThat(Files.readAllBytes(mountPoint.resolve("test.txt")), equalTo(getTestContentBytes()));
    }

    @Test
    public void writeAndTrackSize() throws Exception {

        Path path = mountPoint.resolve("test.txt");

        try (OutputStream stream = Files.newOutputStream(path)) {
            stream.write(Arrays.copyOfRange(getTestContentBytes(), 0, 6));
            assertThat(path.toFile().length(), equalTo((long) 6));
            stream.write(Arrays.copyOfRange(getTestContentBytes(), 6, getTestContentBytes().length));
            assertThat(path.toFile().length(), equalTo((long) getTestContentBytes().length));
        }

        assertThat(path.toFile().length(), equalTo((long) getTestContentBytes().length));
    }

    @Test
    public void truncate() throws Exception {
        Path path = mountPoint.resolve("test.txt");
        Files.write(path, getTestContentBytes());
        lifeCycleManager.waitUntilLocalStorageIsEmpty();
        new FileOutputStream(path.toFile(), true).getChannel().truncate(5).close();
        assertThat(Files.readAllBytes(path), equalTo(Arrays.copyOfRange(getTestContentBytes(), 0, 5)));
    }

    @Test
    public void remove() throws Exception {

        File folder = drive.createFolder("test", testFolder);
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
        lifeCycleManager.waitUntilUploaderIsDone();
        assertThat(Files.exists(filePath), is(false));

        Files.delete(dirPath);
        lifeCycleManager.waitUntilUploaderIsDone();
        assertThat(Files.exists(dirPath), is(false));
    }

    @Test
    public void move() throws Exception {

        File source = drive.createFolder("source", testFolder);
        drive.createFolder("destination", testFolder);
        drive.createFile("test.txt", source, getTestContent());

        resetFileTree();

        Path sourcePath = mountPoint.resolve("source").resolve("test.txt");
        Path destinationPath = mountPoint.resolve("destination").resolve("test_2.txt");

        assertThat(Files.exists(sourcePath), is(true));
        assertThat(Files.exists(destinationPath), is(false));

        Files.move(sourcePath, destinationPath);
        lifeCycleManager.waitUntilUploaderIsDone();
        assertThat(Files.exists(sourcePath), is(false));
        assertThat(Files.exists(destinationPath), is(true));
    }
}