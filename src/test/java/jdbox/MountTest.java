package jdbox;

import jdbox.openedfiles.OpenedFiles;
import org.junit.After;
import org.junit.Test;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.lessThan;

public class MountTest extends BaseMountFileSystemTest {

    @After
    public void tearDown() throws Exception {
        waitUntilSharedFilesAreClosed();
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

    private void waitUntilSharedFilesAreClosed() throws Exception {
        Date start = new Date();
        while (injector.getInstance(OpenedFiles.class).getSharedFilesCount() != 0) {
            Thread.sleep(100);
            assertThat(new Date().getTime() - start.getTime(), lessThan((long) 5000));
        }
    }
}
