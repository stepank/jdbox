package jdbox.content;

import jdbox.utils.OrderedRule;
import jdbox.utils.TestFileProvider;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collection;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

@Category({RollingReadOpenedFile.class, OpenedFiles.class})
@RunWith(Parameterized.class)
public class RollingReadOpenedFileStableTest extends BaseRollingReadOpenedFileTest {

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(
                new Object[][]{{64}, {128}, {192}, {256}, {1024}, {1536}, {2048}, {3072}, {4096}, {8192}});
    }

    @Parameterized.Parameter
    public int count;

    @OrderedRule
    public final TestFileProvider testFileProvider = new TestFileProvider(lifeCycleManager, testFolderProvider, 64 * 1024);

    @Test
    public void read() throws IOException {

        tempStoreFactory.setConfig(new InMemoryByteStoreFactory.Config(128));
        readerFactory.setConfig(new StreamCachingByteSourceFactory.Config(128));
        factory.setConfig(new RollingReadOpenedFileFactory.Config(1024, 4096));

        byte[] content = testFileProvider.getContent();

        try (ByteStore openedFile = factory.create(testFileProvider.getFile())) {

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
}
