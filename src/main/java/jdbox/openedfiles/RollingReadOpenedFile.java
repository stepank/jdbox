package jdbox.openedfiles;

import jdbox.DriveAdapter;
import jdbox.filetree.File;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;

public class RollingReadOpenedFile implements OpenedFile {

    private static final int MIN_PAGE_SIZE = 4 * 1024 * 1024;
    private static final int MAX_PAGE_SIZE = 16 * 1024 * 1024;
    private static final int PAGES_NUMBER = 3;
    private static final double MAX_STRETCH_FACTOR = 1.5;

    private static final Logger logger = LoggerFactory.getLogger(RollingReadOpenedFile.class);

    private final File file;
    private final DriveAdapter drive;
    private final ScheduledExecutorService executor;
    private final int minPageSize;
    private final int maxPageSize;
    private final int readerBufferSize;
    private final Readers readers = new Readers(PAGES_NUMBER);

    private boolean discarded = false;

    public RollingReadOpenedFile(File file, DriveAdapter drive, ScheduledExecutorService executor) {
        this(file, drive, executor, MIN_PAGE_SIZE, MAX_PAGE_SIZE, RangeMappedOpenedFile.READER_BUFFER_SIZE);
    }

    public RollingReadOpenedFile(
            File file, DriveAdapter drive, ScheduledExecutorService executor,
            int minPageSize, int maxPageSize, int readerBufferSize) {
        this.file = file;
        this.drive = drive;
        this.executor = executor;
        this.minPageSize = minPageSize;
        this.maxPageSize = maxPageSize;
        this.readerBufferSize = readerBufferSize;
    }

    @Override
    public File getOrigin() {
        return file;
    }

    @Override
    public synchronized int read(ByteBuffer buffer, long offset, int count) throws Exception {

        logger.debug("reading {}, offset {}, count {}", file, offset, count);

        assert !discarded;
        assert offset < file.getSize();

        count = (int) Math.min(count, file.getSize() - offset);

        int read = 0;

        while (read < count) {

            RangeMappedOpenedFile reader = getReaderAndCreateAhead(offset + read);

            logger.debug("got {}", reader);

            try {
                int bytesToRead = (int) Math.min(count - read, reader.getRigthOffset() - offset - read);
                read += reader.read(buffer, (int) (offset + read - reader.getOffset()), bytesToRead);
            } catch (Exception e) {
                readers.remove(reader);
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
    public void truncate(long offset) throws Exception {
        throw new UnsupportedOperationException("truncate is not supported");
    }

    @Override
    public synchronized void close() throws Exception {
        discarded = true;
        readers.closeAll();
    }

    private RangeMappedOpenedFile getReaderAndCreateAhead(long offset) {

        RangeMappedOpenedFile reader = getOrCreateReader(offset);

        if (reader.getRigthOffset() == file.getSize())
            return reader;

        Readers.Entry nextEntry = readers.ceiling(reader.getRigthOffset());
        if (nextEntry == null || reader.getRigthOffset() < nextEntry.reader.getOffset()) {
            int desiredLength = Integer.highestOneBit(reader.getLength());
            if (desiredLength < maxPageSize)
                desiredLength *= 2;
            long rightBoundary = nextEntry == null ? file.getSize() : nextEntry.reader.getOffset();
            createReader(reader.getRigthOffset(), desiredLength, (int) (rightBoundary - reader.getRigthOffset()));
        }

        return reader;
    }

    private RangeMappedOpenedFile getOrCreateReader(long offset) {

        Readers.Entry floorEntry = readers.floor(offset);
        if (floorEntry != null && floorEntry.reader.getRigthOffset() > offset)
            return floorEntry.touch().reader;

        Readers.Entry ceilingEntry = readers.ceiling(offset);
        if (ceilingEntry == null)
            return createReader(offset, minPageSize, (int) (file.getSize() - offset));

        return createReader(offset, minPageSize, (int) (ceilingEntry.reader.getOffset() - offset));
    }

    private RangeMappedOpenedFile createReader(long offset, int desiredLength, int maxLength) {
        int length = (desiredLength * MAX_STRETCH_FACTOR > maxLength) ? maxLength : desiredLength;
        return readers.create(offset, length, readerBufferSize).reader;
    }

    public String toString() {
        return String.format("RollingReadOpenedFile{file = %s}", file.getName());
    }

    private class Readers {

        private final int maxSize;
        private final List<Entry> entries = new LinkedList<>();
        private long current = 0;

        public Readers(int maxSize) {
            this.maxSize = maxSize;
        }

        public Entry floor(long offset) {
            Entry result = null;
            for (Entry entry : entries) {
                if (entry.reader.getOffset() <= offset &&
                        (result == null || entry.reader.getOffset() > result.reader.getOffset()))
                    result = entry;
            }
            return result;
        }

        public Entry ceiling(long offset) {
            Entry result = null;
            for (Entry entry : entries) {
                if (entry.reader.getOffset() >= offset &&
                        (result == null || entry.reader.getOffset() < result.reader.getOffset()))
                    result = entry;
            }
            return result;
        }

        public Entry create(long offset, int length, int bufferSize) {

            Entry result = new Entry(
                    RangeMappedOpenedFile.create(file, drive, null, executor, offset, length, bufferSize), current++);

            entries.add(result);

            if (entries.size() <= maxSize)
                return result;

            Entry entryToRemove = null;
            int indexToRemove = 0;
            int i = 0;
            for (Entry entry : entries) {
                if (entryToRemove == null || entry.touched < entryToRemove.touched) {
                    entryToRemove = entry;
                    indexToRemove = i;
                }
                i++;
            }
            if (entryToRemove != null) {
                entries.remove(indexToRemove);
                try {
                    entryToRemove.reader.close();
                } catch (Exception e) {
                    logger.error("an error occured while closing an evicted reader", e);
                }
            }

            return result;
        }

        public void remove(RangeMappedOpenedFile reader) {
            for (Iterator<Entry> iterator = entries.iterator(); iterator.hasNext(); ) {
                if (iterator.next().reader == reader) {
                    iterator.remove();
                    break;
                }
            }
        }

        public void closeAll() throws Exception {
            for (Entry entry : entries) {
                entry.reader.close();
            }
            entries.clear();
        }

        private class Entry {

            final RangeMappedOpenedFile reader;
            long touched;

            Entry(RangeMappedOpenedFile reader, long touched) {
                this.reader = reader;
                this.touched = touched;
            }

            public Entry touch() {
                touched = current++;
                return this;
            }
        }
    }
}
