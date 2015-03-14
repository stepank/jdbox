package jdbox;

import org.junit.After;
import org.junit.Test;

import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class MountTest extends BaseMountFileSystemTest {

    @After
    public void tearDown() throws Exception {
        waitUntilSharedFilesAreClosed(5000);
        super.tearDown();
    }

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
        waitUntilSharedFilesAreClosed(5000);
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
        waitUntilSharedFilesAreClosed(5000);
        new FileOutputStream(path.toFile(), true).getChannel().truncate(5).close();
        waitUntilSharedFilesAreClosed(5000);
        assertThat(new String(Files.readAllBytes(path)), equalTo(testContentString.substring(0, 5)));
    }
}
