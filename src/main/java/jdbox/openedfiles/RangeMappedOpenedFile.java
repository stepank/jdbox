package jdbox.openedfiles;

import com.google.common.io.ByteSource;
import com.google.common.io.ByteStreams;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import jdbox.DriveAdapter;
import jdbox.Uploader;
import jdbox.filetree.File;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * Even though this opened file is mapped on a range of a cloud file, all methods accepting offset treat it as
 * starting from the beginning of that range, not from the beginning of of the cloud file.
 */
public class RangeMappedOpenedFile implements OpenedFile {

    private static final Logger logger = LoggerFactory.getLogger(RangeMappedOpenedFile.class);

    private static final int BUFFER_SIZE = 128 * 1024;

    private final int bufferSize;
    private final long offset;
    private final long initialLength;
    private final File file;
    private final DriveAdapter drive;
    private final Future<InputStream> stream;
    private final Uploader uploader;

    private int available = 0;
    private boolean hasChanged = false;
    private boolean discarded = false;

    private int length;
    private final List<byte[]> buffers = new ArrayList<>();

    public static RangeMappedOpenedFile create(
            File file, DriveAdapter drive, Uploader uploader, ExecutorService executor) {
        return create(file, drive, uploader, executor, 0, file.getSize(), BUFFER_SIZE);
    }

    public static RangeMappedOpenedFile create(
            File file, DriveAdapter drive, Uploader uploader, ExecutorService executor, int bufferSize) {
        return create(file, drive, uploader, executor, 0, file.getSize(), bufferSize);
    }

    public static RangeMappedOpenedFile create(
            File file, DriveAdapter drive, Uploader uploader, ExecutorService executor, long offset, long length) {
        return create(file, drive, uploader, executor, offset, length, BUFFER_SIZE);
    }

    public static RangeMappedOpenedFile create(
            final File file, final DriveAdapter drive, Uploader uploader, ExecutorService executor,
            final long offset, final long length, int bufferSize) {

        final SettableFuture<InputStream> streamFuture;

        if (!file.isDownloadable())
            streamFuture = null;
        else {

            logger.debug("requesting a stream of {}, offset {}, length {}", file, offset, length);

            streamFuture = SettableFuture.create();
            final Date start = new Date();
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        InputStream stream = drive.downloadFileRange(file, offset, length);
                        logger.debug(
                                "got a stream of {}, offset {}, length {}, exec time {} ms",
                                file, offset, length, new Date().getTime() - start.getTime());
                        streamFuture.set(stream);
                    } catch (Exception e) {
                        streamFuture.setException(e);
                    }
                }
            });
        }

        return new RangeMappedOpenedFile(file, drive, uploader, streamFuture, offset, length, bufferSize);
    }

    private RangeMappedOpenedFile(
            File file, DriveAdapter drive, Uploader uploader, Future<InputStream> streamFuture,
            long offset, long length, int bufferSize) {

        if (file.getSize() > Integer.MAX_VALUE)
            throw new UnsupportedOperationException("the file is too large");

        this.file = file;
        this.drive = drive;
        this.uploader = uploader;
        this.stream = streamFuture;
        this.offset = offset;
        this.initialLength = (int) length;
        this.length = (int) length;
        this.bufferSize = bufferSize;

        assert getRigthOffset() <= file.getSize();
    }

    public long getOffset() {
        return offset;
    }

    public int getLength() {
        return length;
    }

    public long getRigthOffset() {
        return offset + length;
    }

    public int getBufferCount() {
        return buffers.size();
    }

    @Override
    public File getOrigin() {
        return file;
    }

    @Override
    public synchronized int read(ByteBuffer buffer, long offset, int count) throws Exception {

        logger.debug("reading {}, offset {}, count {}", file, offset, count);

        assert !discarded;
        assert offset < length;

        count = Math.min(count, length - (int) offset);

        ensureContentIsAvailable(offset + count);

        int read = 0;

        while (read < count) {

            int n = ((int) offset + read) / bufferSize;
            assert n < buffers.size();

            byte[] src = buffers.get(n);

            int srcOffset = (int) offset + read - bufferSize * n;
            int bytesToRead = Math.min(count - read, bufferSize - srcOffset);

            buffer.put(src, srcOffset, bytesToRead);
            read += bytesToRead;
        }

        logger.debug("done reading {}, offset {}, count {}", file, offset, count);

        return read;
    }

    @Override
    public synchronized int write(ByteBuffer buffer, long offset, int count) throws Exception {

        logger.debug("writing {}, offset {}, count {}", file, offset, count);

        assert uploader != null;
        assert !discarded;
        assert offset <= length;

        ensureContentIsAvailable(offset + count);

        int written = 0;

        while (written < count) {

            int n = ((int) offset + written) / bufferSize;

            assert n <= buffers.size();

            byte[] dst;
            if (n < buffers.size())
                dst = buffers.get(n);
            else {
                dst = new byte[bufferSize];
                buffers.add(dst);
            }

            int dstOffset = (int) offset + written - bufferSize * n;
            int bytesToWrite = Math.min(count - written, bufferSize - dstOffset);

            buffer.get(dst, dstOffset, bytesToWrite);
            written += bytesToWrite;
        }

        if (written > 0)
            hasChanged = true;

        length = Math.max(length, (int) offset + written);

        file.setSize(length);

        logger.debug("done writing {}, offset {}, count {}", file, offset, count);

        return written;
    }

    public synchronized ListenableFuture flush() {

        assert !discarded;

        if (!hasChanged)
            return null;

        hasChanged = false;

        final byte[] buffer = new byte[length];
        int position = 0;
        for (byte[] tmp : buffers) {
            System.arraycopy(tmp, 0, buffer, position, Math.min(tmp.length, length - position));
            position += tmp.length;
        }

        return uploader.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    file.update(drive.updateFileContent(file, ByteSource.wrap(buffer).openStream()));
                } catch (Exception e) {
                    logger.error("an error ocured while updating file content", e);
                }
            }
        });
    }

    @Override
    public synchronized void close() throws Exception {
        discarded = true;
        if (stream != null)
            stream.get().close();
    }

    private void ensureContentIsAvailable(long required) throws Exception {

        if (stream == null)
            return;

        required = Math.min(initialLength, required);

        while (available < required) {

            int n = available / bufferSize;

            assert n == buffers.size() || n == buffers.size() - 1;

            byte[] dst;
            if (n < buffers.size())
                dst = buffers.get(n);
            else {
                dst = new byte[bufferSize];
                buffers.add(dst);
            }

            int dstOffset = available - bufferSize * n;
            int bytesToRead = Math.min((int) (required - available), bufferSize - dstOffset);

            ByteStreams.readFully(this.stream.get(), dst, dstOffset, bytesToRead);

            available += bytesToRead;
        }
    }

    public String toString() {
        return String.format(
                "RangeMappedOpenedFile{file = %s, offset = %s, length = %s}", file.getName(), offset, length);
    }
}
