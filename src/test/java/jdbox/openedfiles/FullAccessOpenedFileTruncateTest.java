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

@Category(FullAccessOpenedFile.class)
@RunWith(Parameterized.class)
public class FullAccessOpenedFileTruncateTest extends BaseFullAccessOpenedFileTest {

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{{2}, {4}, {6}, {8}, {22}, {24}});
    }

    @Parameterized.Parameter
    public int length;

    private File file;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        file = drive.createFile(testFileName, testDir, getTestContent());
    }

    @Test
    public void truncate() throws Exception {

        byte[] expected = new byte[length];
        System.arraycopy(testContentString.getBytes(), 0, expected, 0, Math.min(length, testContentString.length()));

        try (ByteStore openedFile = factory.create(file)) {

            openedFile.truncate(length);

            ByteBuffer buffer = ByteBuffer.allocate(length);

            assertThat(openedFile.read(buffer, 0, expected.length), equalTo(expected.length));
            assertThat(buffer.array(), equalTo(expected));
        }

        waitUntilSharedFilesAreClosed();

        try (ByteStore openedFile = factory.create(file)) {

            ByteBuffer buffer = ByteBuffer.allocate(length);

            assertThat(openedFile.read(buffer, 0, expected.length), equalTo(expected.length));
            assertThat(buffer.array(), equalTo(expected));
        }

        waitUntilSharedFilesAreClosed();
    }
}
