package jdbox.openedfiles;

import jdbox.BaseTest;
import jdbox.Uploader;
import jdbox.filetree.File;
import org.junit.Before;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.concurrent.ScheduledExecutorService;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class RangeMappedOpenedFileTest extends BaseTest {

    private final static int bufferSize = 4;

    File file;
    RangeMappedOpenedFile openedFile;
    Uploader uploader;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        uploader = injector.getInstance(Uploader.class);
        file = drive.createFile(testFileName, testDir, getTestContent());
    }

    @Test
    public void read() throws Exception {
        runReadTest(11);
        runReadTest(16);
        runReadTest(4, 4, 3);
        runReadTest(4, 4, 4);
        runReadTest(2, 4, 4, 1);
        runReadTest(2, 4, 4, 4);
        runReadTest(8, 3);
        runReadTest(8, 8);
        runReadTest(2, 8, 1);
        runReadTest(2, 8, 8);
    }

    private void runReadTest(int... counts) throws Exception {

        openedFile = RangeMappedOpenedFile.create(
                file, drive, uploader, injector.getInstance(ScheduledExecutorService.class), bufferSize);
        assertThat(openedFile.getBufferCount(), equalTo(0));

        byte[] bytes = new byte[testContentString.length()];

        int offset = 0;
        for (int count : counts) {

            int bytesToRead = Math.min(bytes.length - offset, count);

            ByteBuffer buffer = ByteBuffer.allocate(bytesToRead);
            assertThat(openedFile.read(buffer, offset, bytesToRead), equalTo(bytesToRead));

            buffer.rewind();
            buffer.get(bytes, offset, bytesToRead);

            offset += count;
        }

        assertThat(bytes, equalTo(testContentString.getBytes()));
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

        openedFile = RangeMappedOpenedFile.create(
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

        openedFile = RangeMappedOpenedFile.create(
                file, drive, uploader, injector.getInstance(ScheduledExecutorService.class), bufferSize);

        buffer.rewind();
        assertThat(openedFile.read(buffer, 0, bytes.length), equalTo(bytes.length));
        assertThat(buffer.array(), equalTo(bytes));
        assertThat(openedFile.getBufferCount(), equalTo(4));
    }
}
