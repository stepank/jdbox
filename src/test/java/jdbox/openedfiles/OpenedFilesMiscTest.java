package jdbox.openedfiles;

import jdbox.filetree.File;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.nio.ByteBuffer;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

@Category(OpenedFiles.class)
public class OpenedFilesMiscTest extends BaseOpenedFilesTest {

    @Test
    public void partialReadWrite() throws Exception {

        File file = drive.createFile(testFileName, testDir, getTestContent());

        String replacement = "abcd";
        int offset = 3;

        try (ByteStore openedFile = openedFiles.open(file, OpenedFiles.OpenMode.READ_WRITE)) {

            ByteBuffer buffer = ByteBuffer.allocate(replacement.length());
            buffer.put(replacement.getBytes(), 0, replacement.length());

            buffer.rewind();
            assertThat(openedFile.write(buffer, offset, replacement.length()), equalTo(replacement.length()));
        }

        waitUntilLocalStorageIsEmpty();

        try (ByteStore openedFile = openedFiles.open(file, OpenedFiles.OpenMode.READ_WRITE)) {

            String expected =
                    testContentString.substring(0, offset) +
                            replacement + testContentString.substring(offset + replacement.length());

            ByteBuffer buffer = ByteBuffer.allocate(expected.length());

            assertThat(openedFile.read(buffer, 0, expected.length()), equalTo(expected.length()));
            assertThat(buffer.array(), equalTo(expected.getBytes()));
        }

        waitUntilLocalStorageIsEmpty();
    }

    @Test
    public void fileBecomesLarge() throws Exception {

        openedFiles.setConfig(new OpenedFiles.Config(testContentString.length() + 4));

        File file = drive.createFile(testFileName, testDir, getTestContent());

        try (ByteStore openedFile = openedFiles.open(file, OpenedFiles.OpenMode.READ_WRITE)) {

            String addition = UUID.randomUUID().toString();
            byte[] bytes = addition.getBytes();

            assertThat(
                    openedFile.write(ByteBuffer.wrap(bytes), testContentString.length(), bytes.length),
                    equalTo(bytes.length));

            try (ByteStore openedFile2 = openedFiles.open(file, OpenedFiles.OpenMode.READ_WRITE)) {

                String expected = testContentString + addition;
                ByteBuffer buffer = ByteBuffer.allocate(expected.length());

                assertThat(openedFile2.read(buffer, 0, expected.length()), equalTo(expected.length()));
                assertThat(buffer.array(), equalTo(expected.getBytes()));
            }
        }

        waitUntilLocalStorageIsEmpty();
    }
}