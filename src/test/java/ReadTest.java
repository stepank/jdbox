import com.google.common.io.ByteStreams;
import jdbox.JdBox;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Paths;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class ReadTest extends BaseFileSystemTest {

    @Test
    public void simple() throws Exception {
        String actual = new String(Files.readAllBytes(Paths.get(localPath("/test.txt"))));
        String expected = new String(ByteStreams.toByteArray(JdBox.class.getResourceAsStream("/test.txt")));
        assertThat(actual, equalTo(expected));
    }
}
