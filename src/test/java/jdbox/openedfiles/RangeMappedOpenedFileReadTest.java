package jdbox.openedfiles;

import jdbox.filetree.File;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collection;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

@Category(RangeMappedOpenedFile.class)
@RunWith(Parameterized.class)
public class RangeMappedOpenedFileReadTest extends BaseRangeMappedOpenedFileTest {

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                {new int[]{11}},
                {new int[]{16}},
                {new int[]{4, 4, 3}},
                {new int[]{4, 4, 4}},
                {new int[]{2, 4, 4, 1}},
                {new int[]{8, 3}},
                {new int[]{8, 8}},
                {new int[]{2, 8, 1}},
                {new int[]{2, 8, 8}},
        });
    }

    @Parameterized.Parameter
    public int[] counts;

    private File file;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        file = drive.createFile(testFileName, testDir, getTestContent());
    }

    @Test
    public void read() throws Exception {

        byte[] content = testContentString.getBytes();

        RangeMappedOpenedFile openedFile = factory.create(file);
        assertThat(openedFile.getBufferCount(), equalTo(0));

        byte[] bytes = new byte[content.length];

        int offset = 0;
        for (int count : counts) {

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
        }

        assertThat(bytes, equalTo(content));
        assertThat(openedFile.getBufferCount(), equalTo(3));
    }
}