package jdbox.content.filetypes;

import jdbox.content.OpenedFiles;
import jdbox.content.bytestores.ByteStore;
import jdbox.content.bytestores.InMemoryByteStoreFactory;
import jdbox.models.File;
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

@Category({RollingReadOpenedFileFactory.class, OpenedFiles.class})
public class RollingReadOpenedFileFuzzyTest extends BaseRollingReadOpenedFileTest {

    private static final Logger logger = LoggerFactory.getLogger(RollingReadOpenedFileFuzzyTest.class);

    @OrderedRule
    public final TestFileProvider testFileProvider = new TestFileProvider(lifeCycleManager, testFolderProvider, 1024 * 1024);

    @Test
    public void fuzzyRead() throws InterruptedException, IOException {

        tempStoreFactory.setConfig(new InMemoryByteStoreFactory.Config(512));
        factory.setConfig(new RollingReadOpenedFileFactory.Config(512, 2048, 8192));

        Random random = new Random();

        File file = testFileProvider.getFile();
        byte[] content = testFileProvider.getContent();

        final int[] maxReadChunkSizes = new int[]{1024, 4 * 1024, 16 * 1024};

        for (int maxReadChunkSize : maxReadChunkSizes) {

            logger.info("max read chunk size is {}", maxReadChunkSize);

            try (ByteStore openedFile = factory.create(file)) {

                ByteBuffer buffer = ByteBuffer.allocate(maxReadChunkSize);

                for (int i = 0; i < 50; i++) {

                    int offset = random.nextInt(content.length);
                    int bytesToRead = 1 + random.nextInt(maxReadChunkSize);
                    int expectedRead = Math.min(content.length - offset, bytesToRead);

                    buffer.rewind();
                    assertThat(openedFile.read(buffer, offset, expectedRead), equalTo(expectedRead));

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
