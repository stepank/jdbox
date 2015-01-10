package jdbox.filereaders;

import jdbox.DriveAdapter;
import jdbox.filetree.File;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class RollingFileReader implements FileReader {

    private static final int MIN_PAGE_SIZE = 4 * 1024 * 1024;
    private static final int MAX_PAGE_SIZE = 16 * 1024 * 1024;
    private static final int PAGE_EXPIRY_IN_SECS = 10;

    private static final Logger logger = LoggerFactory.getLogger(RollingFileReader.class);

    private final File file;
    private final DriveAdapter drive;
    private final ScheduledExecutorService executor;
    private final NavigableMap<Long, Entry> readers = new TreeMap<>();

    private boolean discarded = false;

    public RollingFileReader(File file, DriveAdapter drive, ScheduledExecutorService executor) {
        this.file = file;
        this.drive = drive;
        this.executor = executor;
    }

    @Override
    public synchronized void read(ByteBuffer buffer, long offset, int count) throws Exception {

        logger.debug("reading {}, offset {}, count {}", file, offset, count);

        if (discarded)
            throw new IllegalStateException("file reader is discarded");

        if (offset + count > file.getSize())
            throw new IndexOutOfBoundsException();

        while (count > 0) {

            RangeConstrainedFileReader reader = getReader(offset);

            logger.debug("got {}", reader);

            try {

                int bytesToRead = (int) Math.min(count, reader.rightOffset - offset);

                reader.read(buffer, (int) (offset - reader.offset), bytesToRead);

                count -= bytesToRead;
                offset += bytesToRead;

            } catch (Exception e) {
                readers.remove(reader.offset);
                throw e;
            }
        }

        logger.debug("done reading {}, offset {}, count {}", file, offset, count);
    }

    @Override
    public synchronized void discard() throws Exception {
        discarded = true;
        for (Entry e : readers.values()) {
            e.reader.discard();
        }
    }

    private RangeConstrainedFileReader getReader(long offset) {

        Entry entry = getOrCreateReader(offset);

        if (entry.expiry == null)
            readers.put(offset, entry);

        scheduleReaderExpiry(entry);

        Entry nextEntry = readers.get(entry.reader.rightOffset);
        if (nextEntry == null &&
                entry.reader.rightOffset < file.getSize() && offset - entry.reader.offset > entry.reader.length / 2) {
            nextEntry = new Entry(createReader(
                    entry.reader.rightOffset,
                    Math.min(MAX_PAGE_SIZE, entry.reader.length * 2), (int) (file.getSize() - entry.reader.rightOffset)));
            readers.put(entry.reader.rightOffset, nextEntry);
        }

        if (nextEntry != null)
            scheduleReaderExpiry(nextEntry);

        return entry.reader;
    }

    private Entry getOrCreateReader(long offset) {

        Map.Entry<Long, Entry> mapEntry = readers.floorEntry(offset);
        if (mapEntry != null) {
            Entry floorEntry = mapEntry.getValue();
            RangeConstrainedFileReader reader = floorEntry.reader;
            if (reader.rightOffset > offset)
                return floorEntry;
        }

        Entry ceilingEntry = null;

        mapEntry = readers.ceilingEntry(offset);
        if (mapEntry != null)
            ceilingEntry = mapEntry.getValue();

        if (ceilingEntry == null)
            return new Entry(createReader(offset, MIN_PAGE_SIZE, (int) (file.getSize() - offset)));

        return new Entry(createReader(offset, MIN_PAGE_SIZE, (int) (ceilingEntry.reader.offset - offset)));
    }

    private RangeConstrainedFileReader createReader(long offset, int desiredLength, int maxLength) {
        return RangeConstrainedFileReader.create(
                file, drive, executor, offset, (maxLength < desiredLength * 5 / 4) ? maxLength : desiredLength);
    }

    private void scheduleReaderExpiry(Entry entry) {

        if (entry.expiry != null)
            entry.expiry.cancel(false);

        final RangeConstrainedFileReader reader = entry.reader;

        logger.debug("scheduling {} to be discarded", reader);
        entry.expiry = executor.schedule(new Runnable() {
            @Override
            public void run() {
                logger.debug("discarding {}", reader);
                readers.remove(reader.offset);
            }
        }, PAGE_EXPIRY_IN_SECS, TimeUnit.SECONDS);
    }

    public String toString() {
        return String.format("RollingFileReader{file = %s}", file.getName());
    }

    private class Entry {

        public final RangeConstrainedFileReader reader;
        private ScheduledFuture expiry = null;

        private Entry(RangeConstrainedFileReader reader) {
            this.reader = reader;
        }
    }
}
