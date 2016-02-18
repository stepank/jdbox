package jdbox.content.bytestores;

import jdbox.content.OpenedFiles;
import org.junit.Before;
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

@Category(OpenedFiles.class)
@RunWith(Parameterized.class)
public class InMemoryByteStoreTest {

    protected InMemoryByteStoreFactory factory;

    @Before
    public void setUp() {
        factory = new InMemoryByteStoreFactory(new InMemoryByteStoreFactory.Config(4));
    }

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                {new int[]{15}},
                {new int[]{16}},
                {new int[]{4, 4, 4, 3}},
                {new int[]{4, 4, 4, 4}},
                {new int[]{2, 4, 4, 4, 1}},
                {new int[]{2, 4, 4, 4, 4}},
                {new int[]{6, 8, 1}},
                {new int[]{6, 8, 4}},
                {new int[]{8, 7}},
                {new int[]{8, 8}},
                {new int[]{2, 8, 5}},
                {new int[]{2, 8, 8}},
        });
    }

    @Parameterized.Parameter
    public int[] counts;

    @Test
    public void writeAndRead() throws IOException {

        byte[] expectedFull = "pysh-pysh-ololo".getBytes();
        byte[] actualFull = new byte[expectedFull.length];

        try (InMemoryByteStore store = factory.create()) {

            assertThat(store.getBufferCount(), equalTo(0));

            int offset = 0;
            for (int count : counts) {

                int bytesToWrite = Math.min(expectedFull.length - offset, count);

                ByteBuffer buffer = ByteBuffer.allocate(bytesToWrite);
                buffer.put(expectedFull, offset, bytesToWrite);

                buffer.rewind();
                assertThat(store.write(buffer, offset, bytesToWrite), equalTo(bytesToWrite));

                offset += count;
            }

            assertThat(store.getBufferCount(), equalTo(4));

            offset = 0;
            for (int count : counts) {

                int expectedBytesRead = Math.min(expectedFull.length - offset, count);

                ByteBuffer buffer = ByteBuffer.allocate(count);
                assertThat(store.read(buffer, offset, count), equalTo(expectedBytesRead));

                byte[] actual = new byte[expectedBytesRead];
                buffer.rewind();
                buffer.get(actual, 0, expectedBytesRead);

                byte[] expected = new byte[expectedBytesRead];
                System.arraycopy(expectedFull, offset, expected, 0, expectedBytesRead);

                assertThat(actual, equalTo(expected));

                buffer.rewind();
                buffer.get(actualFull, offset, expectedBytesRead);

                offset += count;
            }

            assertThat(actualFull, equalTo(expectedFull));
        }
    }
}
