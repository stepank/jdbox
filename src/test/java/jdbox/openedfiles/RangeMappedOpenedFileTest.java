package jdbox.openedfiles;

import jdbox.BaseTest;
import jdbox.Uploader;
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

public class RangeMappedOpenedFileTest extends BaseTest {

    private static final Logger logger = LoggerFactory.getLogger(RangeMappedOpenedFileTest.class);
    private static final int bufferSize = 4;

    @Test
    public void read() throws Exception {

        File file = drive.createFile(testFileName, testDir, getTestContent());

        runReadTest(file, 11);
        runReadTest(file, 16);
        runReadTest(file, 4, 4, 3);
        runReadTest(file, 4, 4, 4);
        runReadTest(file, 2, 4, 4, 1);
        runReadTest(file, 2, 4, 4, 4);
        runReadTest(file, 8, 3);
        runReadTest(file, 8, 8);
        runReadTest(file, 2, 8, 1);
        runReadTest(file, 2, 8, 8);
    }

    private void runReadTest(File file, int... counts) throws Exception {

        byte[] content = testContentString.getBytes();

        RangeMappedOpenedFile openedFile = RangeMappedOpenedFile.create(
                file, drive, null, injector.getInstance(ScheduledExecutorService.class), bufferSize);
        assertThat(openedFile.getBufferCount(), equalTo(0));

        byte[] bytes = new byte[content.length];

        int offset = 0;
        for (int count : counts) {

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
        }

        assertThat(bytes, equalTo(content));
        assertThat(openedFile.getBufferCount(), equalTo(3));
    }

    @Test
    public void write() throws Exception {
        runWriteTest(15);
        runWriteTest(16);
        runWriteTest(4, 4, 4, 3);
        runWriteTest(4, 4, 4, 4);
        runWriteTest(2, 4, 4, 4, 1);
        runWriteTest(2, 4, 4, 4, 4);
        runWriteTest(8, 7);
        runWriteTest(8, 8);
        runWriteTest(2, 8, 5);
        runWriteTest(2, 8, 8);
    }

    public void runWriteTest(int... counts) throws Exception {

        Uploader uploader = injector.getInstance(Uploader.class);

        File file = drive.createFile(testFileName, testDir, getTestContent());

        RangeMappedOpenedFile openedFile = RangeMappedOpenedFile.create(
                file, drive, uploader, injector.getInstance(ScheduledExecutorService.class), bufferSize);
        assertThat(openedFile.getBufferCount(), equalTo(0));

        String content = "pysh-pysh-ololo";
        byte[] bytes = content.getBytes();

        int offset = 0;
        for (int count : counts) {

            int bytesToWrite = Math.min(bytes.length - offset, count);

            ByteBuffer buffer = ByteBuffer.allocate(bytesToWrite);
            buffer.put(bytes, offset, bytesToWrite);

            buffer.rewind();
            assertThat(openedFile.write(buffer, offset, bytesToWrite), equalTo(bytesToWrite));

            offset += count;
        }
        assertThat(openedFile.getBufferCount(), equalTo(4));

        ByteBuffer buffer = ByteBuffer.allocate(bytes.length);

        assertThat(openedFile.read(buffer, 0, bytes.length), equalTo(bytes.length));
        assertThat(buffer.array(), equalTo(bytes));
        assertThat(openedFile.getBufferCount(), equalTo(4));

        openedFile.flush();

        uploader.waitUntilDone();
        openedFile.close();

        openedFile = RangeMappedOpenedFile.create(
                file, drive, uploader, injector.getInstance(ScheduledExecutorService.class), bufferSize);

        buffer.rewind();
        assertThat(openedFile.read(buffer, 0, bytes.length), equalTo(bytes.length));
        assertThat(buffer.array(), equalTo(bytes));
        assertThat(openedFile.getBufferCount(), equalTo(4));

        openedFile.close();
    }

    @Test
    public void fuzzyRead() throws Exception {

        final int contentLength = 1024 * 1024;
        final int[] maxReadChunkSizes =
                new int[]{10, 100, 1024, 4 * 1024, 16 * 1024, 64 * 1024, 256 * 1024, contentLength};

        Random random = new Random();
        byte[] content = new byte[contentLength];
        random.nextBytes(content);

        File file = drive.createFile(testFileName, testDir, new ByteArrayInputStream(content));

        for (int maxReadChunkSize : maxReadChunkSizes) {

            logger.info("max read chunk size is {}", maxReadChunkSize);

            RangeMappedOpenedFile openedFile = RangeMappedOpenedFile.create(
                    file, drive, null, injector.getInstance(ScheduledExecutorService.class), 1024);
            assertThat(openedFile.getBufferCount(), equalTo(0));

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
    }
}
