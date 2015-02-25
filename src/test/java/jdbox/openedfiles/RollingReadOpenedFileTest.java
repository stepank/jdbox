package jdbox.openedfiles;

import jdbox.BaseTest;
import jdbox.filetree.File;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.util.Random;
import java.util.concurrent.ScheduledExecutorService;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class RollingReadOpenedFileTest extends BaseTest {

    private static final Logger logger = LoggerFactory.getLogger(RollingReadOpenedFile.class);

    @Test
    public void read() throws Exception {

        final int contentLength = 64 * 1024;

        Random random = new Random();
        byte[] content = new byte[contentLength];
        random.nextBytes(content);

        File file = drive.createFile(testFileName, testDir, new ByteArrayInputStream(content));

        runReadTest(file, content, 64);
        runReadTest(file, content, 128);
        runReadTest(file, content, 192);
        runReadTest(file, content, 256);
        runReadTest(file, content, 1024);
        runReadTest(file, content, 1536);
        runReadTest(file, content, 2048);
        runReadTest(file, content, 3072);
        runReadTest(file, content, 4096);
        runReadTest(file, content, 8192);
    }

    private void runReadTest(File file, byte[] content, int count) throws Exception {

        RollingReadOpenedFile openedFile = new RollingReadOpenedFile(
                file, drive, injector.getInstance(ScheduledExecutorService.class), 1024, 4096, 128);

        byte[] bytes = new byte[content.length];

        int offset = 0;
        int read = 0;
        while (read < content.length) {

            int expectedRead = Math.min(bytes.length - offset, count);

            ByteBuffer buffer = ByteBuffer.allocate(count);
            assertThat(openedFile.read(buffer, offset, count), equalTo(expectedRead));

            byte[] actual = new byte[expectedRead];
            buffer.rewind();
            buffer.get(actual, 0, expectedRead);

            byte[] expected = new byte[expectedRead];
            System.arraycopy(content, offset, expected, 0, expectedRead);

            assertThat(actual, equalTo(expected));

            buffer.rewind();
            buffer.get(bytes, offset, expectedRead);

            offset += count;
            read += count;
        }

        assertThat(bytes, equalTo(content));
    }

    @Test
    public void fuzzyRead() throws Exception {

        final int contentLength = 1024 * 1024;
        final int[] maxReadChunkSizes = new int[]{1024, 4 * 1024, 16 * 1024};

        Random random = new Random();
        byte[] content = new byte[contentLength];
        random.nextBytes(content);

        File file = drive.createFile(testFileName, testDir, new ByteArrayInputStream(content));

        for (int maxReadChunkSize : maxReadChunkSizes) {

            logger.info("max read chunk size is {}", maxReadChunkSize);

            RollingReadOpenedFile openedFile = new RollingReadOpenedFile(
                    file, drive, injector.getInstance(ScheduledExecutorService.class), 2048, 8192, 512);

            ByteBuffer buffer = ByteBuffer.allocate(maxReadChunkSize);

            for (int i = 0; i < 50; i++) {

                int offset = random.nextInt(contentLength);
                int bytesToRead = 1 + random.nextInt(maxReadChunkSize);
                int expectedRead = Math.min(contentLength - offset, bytesToRead);

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
    }
}
