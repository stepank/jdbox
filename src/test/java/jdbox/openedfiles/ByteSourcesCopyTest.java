package jdbox.openedfiles;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collection;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

@RunWith(Parameterized.class)
public class ByteSourcesCopyTest {

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{{2}, {4}, {6}, {8}, {22}, {24}});
    }

    @Parameterized.Parameter
    public int length;

    @Test
    public void copy() throws Exception {

        byte[] expected = "pysh-pysh-ololo".getBytes();
        byte[] actual = new byte[expected.length];

        ByteStore source = new InMemoryByteStore(1024);
        source.write(ByteBuffer.wrap(expected), 0, expected.length);

        ByteStore destination = new InMemoryByteStore(1024);
        ByteSources.copy(source, destination, length);

        destination.read(ByteBuffer.wrap(actual), 0, actual.length);

        assertThat(actual, equalTo(expected));
    }
}
