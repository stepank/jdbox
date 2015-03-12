package jdbox.openedfiles;

import jdbox.filetree.File;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collection;
import java.util.Random;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

@Category(RollingReadOpenedFile.class)
@RunWith(Parameterized.class)
public class RollingReadOpenedFileStableTest extends BaseRollingReadOpenedFileTest {

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(
                new Object[][]{{64}, {128}, {192}, {256}, {1024}, {1536}, {2048}, {3072}, {4096}, {8192}});
    }

    @Parameterized.Parameter
    public int count;

    @Test
    public void read() throws Exception {

        readerFactory.setConfig(new RangeMappedOpenedFileFactory.Config(128));
        factory.setConfig(new RollingReadOpenedFileFactory.Config(1024, 4096));

        final int contentLength = 64 * 1024;

        Random random = new Random();
        byte[] content = new byte[contentLength];
        random.nextBytes(content);

        File file = drive.createFile(testFileName, testDir, new ByteArrayInputStream(content));

        OpenedFile openedFile = factory.create(file);

        byte[] bytes = new byte[content.length];

        int offset = 0;
        int read = 0;
        while (read < content.length) {

            int expectedRead = Math.min(bytes.length - offset, count);

            ByteBuffer buffer = ByteBuffer.allocate(count);
            assertThat(openedFile.read(buffer, offset, count), equalTo(expectedRead));

            byte[] actual = new byte[expectedRead];
            buffer.rewind();
            buffer.get(actual, 0, expectedRead);

            byte[] expected = new byte[expectedRead];
            System.arraycopy(content, offset, expected, 0, expectedRead);

            assertThat(actual, equalTo(expected));

            buffer.rewind();
            buffer.get(bytes, offset, expectedRead);

            offset += count;
            read += count;
        }

        assertThat(bytes, equalTo(content));
    }
}
