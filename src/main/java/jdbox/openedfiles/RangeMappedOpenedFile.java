package jdbox.openedfiles;

import com.google.common.io.ByteSource;
import com.google.common.io.ByteStreams;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import jdbox.DriveAdapter;
import jdbox.Uploader;
import jdbox.filetree.File;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

/**
 * Even though this opened file is mapped on a range of a cloud file, read and write methods treat offset as
 * starting from the beginning of that range, not from the beginning of the cloud file.
 * <p/>
 * Any of mothods write, read, truncate, flush can potentially block until it reads the data it needs.
 */
public class RangeMappedOpenedFile implements OpenedFile {

    private static final Logger logger = LoggerFactory.getLogger(RangeMappedOpenedFile.class);

    private final int bufferSize;
    private final long offset;
    private final long initialLength;
    private final File file;
    private final DriveAdapter drive;
    private final Uploader uploader;

    private int length;
    private int available = 0;
    private boolean hasChanged = false;
    private boolean discarded = false;
    private Future<InputStream> stream;

    private final List<byte[]> buffers = new ArrayList<>();

    RangeMappedOpenedFile(
            File file, DriveAdapter drive, Uploader uploader,
            Future<InputStream> streamFuture, long offset, long length, int bufferSize) {

        assert offset >= 0;
        assert length > 0 || offset == 0 && length == 0;

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

    @Override
    public synchronized void truncate(long length) throws Exception {

        if (length == this.length)
            return;

        if (length < this.length) {
            this.length = (int) length;
            hasChanged = true;
        } else {

            ensureContentIsAvailable(length);

            ByteBuffer buffer = ByteBuffer.allocate(bufferSize);
            while (this.length < length) {
                buffer.rewind();
                write(buffer, this.length, (int) Math.min(bufferSize, length - this.length));
            }
        }
    }

    public synchronized ListenableFuture flush() throws Exception {

        assert !discarded;

        if (!hasChanged) {
            logger.debug("nothing to flush for {}", file);
            return Futures.immediateFuture(null);
        }

        ensureContentIsAvailable(initialLength);

        logger.debug("flushing {}", file);

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

        if (available >= initialLength || available >= length) {
            stream.get().close();
            stream = null;
        }
    }

    public String toString() {
        return String.format(
                "RangeMappedOpenedFile{file = %s, offset = %s, length = %s}", file.getName(), offset, length);
    }
}
