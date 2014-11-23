package jdbox.filereaders;

import com.google.common.io.ByteStreams;
import com.google.common.util.concurrent.SettableFuture;
import jdbox.DriveAdapter;
import jdbox.filetree.File;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Date;
import java.util.concurrent.ScheduledExecutorService;

public class RangeConstrainedFileReader implements FileReader {

    private static final Logger logger = LoggerFactory.getLogger(RangeConstrainedFileReader.class);

    public final long offset;
    public final int length;
    public final long rightOffset;

    private final File file;
    private final DriveAdapter drive;
    private final byte[] buffer;
    private final SettableFuture<InputStream> stream = SettableFuture.create();
    private int read = 0;

    public static RangeConstrainedFileReader create(File file, DriveAdapter drive, ScheduledExecutorService executor) {
        return create(file, drive, executor, 0, file.getSize());
    }

    public static RangeConstrainedFileReader create(
            File file, DriveAdapter drive, ScheduledExecutorService executor, long offset, long length) {

        final RangeConstrainedFileReader reader = new RangeConstrainedFileReader(file, drive, offset, length);

        logger.debug("requesting a stream of {}, offset {}, length {}", file, offset, length);

        final Date start = new Date();
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    InputStream stream = reader.drive.downloadFileRange(reader.file, reader.offset, reader.length);
                    logger.debug(
                            "got a stream of {}, offset {}, length {}, exec time {} ms",
                            reader.file, reader.offset, reader.length, new Date().getTime() - start.getTime());
                    reader.setStream(stream);
                } catch (Exception e) {
                    reader.setException(e);
                }
            }
        });

        return reader;
    }

    private RangeConstrainedFileReader(File file, DriveAdapter drive, long offset, long length) {

        if (file.getSize() > Integer.MAX_VALUE)
            throw new UnsupportedOperationException("the file is too large");

        rightOffset = offset + length;

        if (rightOffset > file.getSize())
            throw new IndexOutOfBoundsException();

        this.file = file;
        this.drive = drive;
        this.offset = offset;
        this.length = (int) length;

        buffer = new byte[(int) length];
    }

    public void setStream(InputStream stream) {
        this.stream.set(stream);
    }

    public void setException(Exception e) {
        this.stream.setException(e);
    }

    @Override
    public synchronized void read(ByteBuffer buffer, long offset, int count) throws Exception {

        logger.debug("reading {}, offset {}, count {}", file, offset, count);

        if (offset + count > rightOffset)
            throw new IndexOutOfBoundsException();

        if (offset + count > read) {
            InputStream stream = this.stream.get();
            ByteStreams.readFully(stream, this.buffer, read, (int) (offset + count) - read);
            read = (int) offset + count;
        }

        buffer.put(this.buffer, (int) offset, count);

        logger.debug("done reading {}, offset {}, count {}", file, offset, count);
    }

    public String toString() {
        return String.format("RangeConstrainedFileReader{file = %s, offset = %s, length = %s}", file.getName(), offset, length);
    }
}
