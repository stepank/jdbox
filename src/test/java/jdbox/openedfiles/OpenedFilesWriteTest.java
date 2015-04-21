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

@Category(OpenedFiles.class)
@RunWith(Parameterized.class)
public class OpenedFilesWriteTest extends BaseOpenedFilesTest {

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                {new int[]{15}},
                {new int[]{16}},
                {new int[]{4, 4, 4, 3}},
                {new int[]{4, 4, 4, 4}},
                {new int[]{2, 4, 4, 4, 1}},
                {new int[]{2, 4, 4, 4, 4}},
                {new int[]{8, 7}},
                {new int[]{8, 8}},
                {new int[]{2, 8, 5}},
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
    public void write() throws Exception {

        String content = "pysh-pysh-ololo";
        byte[] bytes = content.getBytes();

        try (ByteStore openedFile = openedFiles.open(file, OpenedFiles.OpenMode.READ_WRITE)) {

            int offset = 0;
            for (int count : counts) {

                int bytesToWrite = Math.min(bytes.length - offset, count);

                ByteBuffer buffer = ByteBuffer.allocate(bytesToWrite);
                buffer.put(bytes, offset, bytesToWrite);

                buffer.rewind();
                assertThat(openedFile.write(buffer, offset, bytesToWrite), equalTo(bytesToWrite));

                offset += count;
            }

            ByteBuffer buffer = ByteBuffer.allocate(bytes.length);

            assertThat(openedFile.read(buffer, 0, bytes.length), equalTo(bytes.length));
            assertThat(buffer.array(), equalTo(bytes));
        }

        waitUntilLocalStorageIsEmpty();

        try (ByteStore openedFile = openedFiles.open(file, OpenedFiles.OpenMode.READ_WRITE)) {

            ByteBuffer buffer = ByteBuffer.allocate(bytes.length);

            assertThat(openedFile.read(buffer, 0, bytes.length), equalTo(bytes.length));
            assertThat(buffer.array(), equalTo(bytes));
        }

        waitUntilLocalStorageIsEmpty();
    }
}