package jdbox;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Paths;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class ReadMountTest extends BaseMountFileSystemTest {

    @Test
    public void simple() throws Exception {
        drive.createFile("test.txt", testDir, getTestContent());
        String actual = new String(Files.readAllBytes(mountPoint.resolve(testDir.getName()).resolve("test.txt")));
        assertThat(actual, equalTo(testContentString));
        assertThat(fs.getCurrentFileHandler(), equalTo((long) 1));
        Thread.sleep(100);
        assertThat(fs.getFileReadersCount(), equalTo(0));
    }
}
