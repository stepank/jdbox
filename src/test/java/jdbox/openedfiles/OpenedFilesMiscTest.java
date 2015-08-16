package jdbox.openedfiles;

import jdbox.models.File;
import jdbox.utils.OrderedRule;
import jdbox.utils.TestFileProvider;
import jdbox.utils.TestUtils;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.nio.ByteBuffer;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

@Category(OpenedFiles.class)
public class OpenedFilesMiscTest extends BaseOpenedFilesTest {

    @OrderedRule
    public TestFileProvider testFileProvider = new TestFileProvider(injectorProvider, testFolderProvider, 11);

    @Test
    public void partialReadWrite() throws Exception {

        File file = testFileProvider.getFile();
        byte[] content = testFileProvider.getContent();

        String replacement = "abcd";
        int offset = 3;

        try (ByteStore openedFile = openedFiles.open(file, OpenedFiles.OpenMode.READ_WRITE)) {

            ByteBuffer buffer = ByteBuffer.allocate(replacement.length());
            buffer.put(replacement.getBytes(), 0, replacement.length());

            buffer.rewind();
            assertThat(openedFile.write(buffer, offset, replacement.length()), equalTo(replacement.length()));
        }

        TestUtils.waitUntilLocalStorageIsEmpty(injector);

        try (ByteStore openedFile = openedFiles.open(testFileProvider.getFile(), OpenedFiles.OpenMode.READ_WRITE)) {

            byte[] expected = new byte[content.length];
            System.arraycopy(content, 0, expected, 0, content.length);
            System.arraycopy(replacement.getBytes(), 0, expected, offset, replacement.length());

            ByteBuffer buffer = ByteBuffer.allocate(expected.length);

            assertThat(openedFile.read(buffer, 0, expected.length), equalTo(expected.length));
            assertThat(buffer.array(), equalTo(expected));
        }

        TestUtils.waitUntilLocalStorageIsEmpty(injector);
    }

    @Test
    public void fileBecomesLarge() throws Exception {

        File file = testFileProvider.getFile();
        byte[] content = testFileProvider.getContent();

        openedFiles.setConfig(new OpenedFiles.Config(content.length + 4));

        try (ByteStore openedFile = openedFiles.open(file, OpenedFiles.OpenMode.READ_WRITE)) {

            byte[] addition = UUID.randomUUID().toString().getBytes();

            assertThat(
                    openedFile.write(ByteBuffer.wrap(addition), content.length, addition.length),
                    equalTo(addition.length));

            try (ByteStore openedFile2 = openedFiles.open(file, OpenedFiles.OpenMode.READ_WRITE)) {

                byte[] expected = new byte[content.length + addition.length];
                System.arraycopy(content, 0, expected, 0, content.length);
                System.arraycopy(addition, 0, expected, content.length, addition.length);

                ByteBuffer buffer = ByteBuffer.allocate(expected.length);

                assertThat(openedFile2.read(buffer, 0, expected.length), equalTo(expected.length));
                assertThat(buffer.array(), equalTo(expected));
            }
        }

        TestUtils.waitUntilLocalStorageIsEmpty(injector);
    }
}