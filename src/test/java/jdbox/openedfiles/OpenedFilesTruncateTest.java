package jdbox.openedfiles;

import jdbox.models.File;
import jdbox.utils.OrderedRule;
import jdbox.utils.TestFileProvider;
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
public class OpenedFilesTruncateTest extends BaseOpenedFilesTest {

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{{0}, {2}, {4}, {6}, {8}, {22}, {24}});
    }

    @Parameterized.Parameter
    public int length;

    @OrderedRule
    public final TestFileProvider testFileProvider = new TestFileProvider(lifeCycleManager, testFolderProvider, 11);

    @Test
    public void truncate() throws Exception {

        File file = testFileProvider.getFile();
        byte[] content = testFileProvider.getContent();

        byte[] expected = new byte[length];
        System.arraycopy(content, 0, expected, 0, Math.min(length, content.length));

        try (ByteStore openedFile = openedFiles.open(file, OpenedFiles.OpenMode.READ_WRITE)) {

            openedFile.truncate(length);

            ByteBuffer buffer = ByteBuffer.allocate(length);

            assertThat(openedFile.read(buffer, 0, expected.length), equalTo(expected.length));
            assertThat(buffer.array(), equalTo(expected));
        }

        lifeCycleManager.waitUntilLocalStorageIsEmpty();

        try (ByteStore openedFile = openedFiles.open(file, OpenedFiles.OpenMode.READ_WRITE)) {

            ByteBuffer buffer = ByteBuffer.allocate(length);

            assertThat(openedFile.read(buffer, 0, expected.length), equalTo(expected.length));
            assertThat(buffer.array(), equalTo(expected));
        }

        lifeCycleManager.waitUntilLocalStorageIsEmpty();
    }
}
