package jdbox.openedfiles;

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

public class RollingReadOpenedFile implements OpenedFile {

    private static final int MIN_PAGE_SIZE = 4 * 1024 * 1024;
    private static final int MAX_PAGE_SIZE = 16 * 1024 * 1024;
    private static final int PAGE_EXPIRY_IN_SECS = 10;

    private static final Logger logger = LoggerFactory.getLogger(RollingReadOpenedFile.class);

    private final File file;
    private final DriveAdapter drive;
    private final ScheduledExecutorService executor;
    private final NavigableMap<Long, Entry> readers = new TreeMap<>();

    private boolean discarded = false;

    public RollingReadOpenedFile(File file, DriveAdapter drive, ScheduledExecutorService executor) {
        this.file = file;
        this.drive = drive;
        this.executor = executor;
    }

    @Override
    public File getOrigin() {
        return file;
    }

    @Override
    public synchronized int read(ByteBuffer buffer, long offset, int count) throws Exception {

        logger.debug("reading {}, offset {}, count {}", file, offset, count);

        assert !discarded;
        assert offset + count < file.getSize();

        int read = 0;

        while (read < count) {

            RangeMappedOpenedFile reader = getReader(offset);

            logger.debug("got {}", reader);

            try {
                int bytesToRead = (int) Math.min(count - read, reader.getRigthOffset() - offset);
                read += reader.read(buffer, (int) (offset - reader.getOffset()), bytesToRead);
            } catch (Exception e) {
                readers.remove(reader.getOffset());
                throw e;
            }
        }

        logger.debug("done reading {}, offset {}, count {}", file, offset, count);

        return read;
    }

    @Override
    public int write(ByteBuffer buffer, long offset, int count) throws Exception {
        throw new UnsupportedOperationException("write is not supported");
    }

    @Override
    public synchronized void close() throws Exception {
        discarded = true;
        for (Entry e : readers.values()) {
            e.reader.close();
        }
    }

    private RangeMappedOpenedFile getReader(long offset) {

        Entry entry = getOrCreateReader(offset);

        if (entry.expiry == null)
            readers.put(offset, entry);

        scheduleReaderExpiry(entry);

        Entry nextEntry = readers.get(entry.reader.getRigthOffset());
        if (nextEntry == null &&
                entry.reader.getRigthOffset() < file.getSize() &&
                offset - entry.reader.getOffset() > entry.reader.getLength() / 2) {
            nextEntry = new Entry(createReader(
                    entry.reader.getRigthOffset(),
                    Math.min(MAX_PAGE_SIZE, entry.reader.getLength() * 2),
                    (int) (file.getSize() - entry.reader.getRigthOffset())));
            readers.put(entry.reader.getRigthOffset(), nextEntry);
        }

        if (nextEntry != null)
            scheduleReaderExpiry(nextEntry);

        return entry.reader;
    }

    private Entry getOrCreateReader(long offset) {

        Map.Entry<Long, Entry> mapEntry = readers.floorEntry(offset);
        if (mapEntry != null) {
            Entry floorEntry = mapEntry.getValue();
            RangeMappedOpenedFile reader = floorEntry.reader;
            if (reader.getRigthOffset() > offset)
                return floorEntry;
        }

        Entry ceilingEntry = null;

        mapEntry = readers.ceilingEntry(offset);
        if (mapEntry != null)
            ceilingEntry = mapEntry.getValue();

        if (ceilingEntry == null)
            return new Entry(createReader(offset, MIN_PAGE_SIZE, (int) (file.getSize() - offset)));

        return new Entry(createReader(offset, MIN_PAGE_SIZE, (int) (ceilingEntry.reader.getOffset() - offset)));
    }

    private RangeMappedOpenedFile createReader(long offset, int desiredLength, int maxLength) {
        return RangeMappedOpenedFile.create(
                file, drive, null, executor, offset, (maxLength < desiredLength * 5 / 4) ? maxLength : desiredLength);
    }

    private void scheduleReaderExpiry(Entry entry) {

        if (entry.expiry != null)
            entry.expiry.cancel(false);

        final RangeMappedOpenedFile reader = entry.reader;

        logger.debug("scheduling {} to be discarded", reader);
        entry.expiry = executor.schedule(new Runnable() {
            @Override
            public void run() {
                logger.debug("discarding {}", reader);
                readers.remove(reader.getOffset());
            }
        }, PAGE_EXPIRY_IN_SECS, TimeUnit.SECONDS);
    }

    public String toString() {
        return String.format("RollingReadOpenedFile{file = %s}", file.getName());
    }

    private class Entry {

        public final RangeMappedOpenedFile reader;
        private ScheduledFuture expiry = null;

        private Entry(RangeMappedOpenedFile reader) {
            this.reader = reader;
        }
    }
}
