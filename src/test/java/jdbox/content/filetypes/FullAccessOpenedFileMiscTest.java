package jdbox.content.filetypes;

import jdbox.content.OpenedFiles;
import jdbox.content.bytestores.ByteStore;
import jdbox.content.bytestores.InMemoryByteStoreFactory;
import jdbox.utils.OrderedRule;
import jdbox.utils.TestFileProvider;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Random;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

@Category({FullAccessOpenedFileFactory.class, OpenedFiles.class})
public class FullAccessOpenedFileMiscTest extends BaseFullAccessOpenedFileTest {

    private static final Logger logger = LoggerFactory.getLogger(FullAccessOpenedFileMiscTest.class);

    @OrderedRule
    public final TestFileProvider testFileProvider =
            new TestFileProvider(lifeCycleManager, testFolderProvider, 1024 * 1024);

    @Test
    public void fuzzyRead() throws InterruptedException, IOException {

        tempStoreFactory.setConfig(new InMemoryByteStoreFactory.Config(1024));
        factory.setConfig(new FullAccessOpenedFileFactory.Config(1024));

        byte[] content = testFileProvider.getContent();

        Random random = new Random();
        final int[] maxReadChunkSizes =
                new int[]{10, 100, 1024, 4 * 1024, 16 * 1024, 64 * 1024, 256 * 1024, content.length};

        for (int maxReadChunkSize : maxReadChunkSizes) {

            logger.info("max read chunk size is {}", maxReadChunkSize);

            try (ByteStore openedFile = factory.create(testFileProvider.getFile())) {

                ByteBuffer buffer = ByteBuffer.allocate(maxReadChunkSize);

                for (int i = 0; i < 100; i++) {

                    int offset = random.nextInt(content.length);
                    int bytesToRead = 1 + random.nextInt(maxReadChunkSize);
                    int expectedRead = Math.min(content.length - offset, bytesToRead);

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

            lifeCycleManager.waitUntilLocalStorageIsEmpty();
        }
    }
}