package jdbox.openedfiles;

import jdbox.filetree.File;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.util.Random;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

@Category(FullAccessOpenedFile.class)
public class FullAccessOpenedFileMiscTest extends BaseFullAccessOpenedFileTest {

    private static final Logger logger = LoggerFactory.getLogger(FullAccessOpenedFileMiscTest.class);

    @Test
    public void fuzzyRead() throws Exception {

        tempStoreFactory.setConfig(new InMemoryByteStoreFactory.Config(1024));
        readerFactory.setConfig(new StreamCachingByteSourceFactory.Config(1024));

        final int contentLength = 1024 * 1024;
        final int[] maxReadChunkSizes =
                new int[]{10, 100, 1024, 4 * 1024, 16 * 1024, 64 * 1024, 256 * 1024, contentLength};

        Random random = new Random();
        byte[] content = new byte[contentLength];
        random.nextBytes(content);

        File file = drive.createFile(testFileName, testDir, new ByteArrayInputStream(content));

        for (int maxReadChunkSize : maxReadChunkSizes) {

            logger.info("max read chunk size is {}", maxReadChunkSize);

            try (ByteStore openedFile = factory.create(file)) {

                ByteBuffer buffer = ByteBuffer.allocate(maxReadChunkSize);

                for (int i = 0; i < 100; i++) {

                    int offset = random.nextInt(contentLength);
                    int bytesToRead = 1 + random.nextInt(maxReadChunkSize);
                    int expectedRead = Math.min(contentLength - offset, bytesToRead);

                    buffer.rewind();
                    assertThat(openedFile.read(buffer, offset, bytesToRead), equalTo(expectedRead));

                    byte[] actual = new byte[expectedRead];
                    buffer.rewind();
                    buffer.get(actual, 0, expectedRead);

                    byte[] expected = new byte[expectedRead];
                    System.arraycopy(content, offset, expected, 0, expectedRead);

                    assertThat(actual, equalTo(expected));
                }
            }

            waitUntilLocalStorageIsEmpty();
        }
    }
}