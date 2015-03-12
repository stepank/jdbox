package jdbox.openedfiles;

import com.google.inject.Inject;
import jdbox.filetree.File;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class RollingReadOpenedFile implements OpenedFile {

    private static final int PAGES_NUMBER = 3;
    private static final double MAX_STRETCH_FACTOR = 1.5;

    private static final Logger logger = LoggerFactory.getLogger(RollingReadOpenedFile.class);

    private final File file;
    private final RangeMappedOpenedFileFactory readerFactory;
    private final int minPageSize;
    private final int maxPageSize;
    private final Readers readers = new Readers(PAGES_NUMBER);

    private boolean discarded = false;

    RollingReadOpenedFile(
            File file, RangeMappedOpenedFileFactory readerFactory, int minPageSize, int maxPageSize) {
        this.file = file;
        this.readerFactory = readerFactory;
        this.minPageSize = minPageSize;
        this.maxPageSize = maxPageSize;
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
        return readers.create(offset, length).reader;
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

        public Entry create(long offset, int length) {

            Entry result = new Entry(readerFactory.create(file, offset, length), current++);

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

class RollingReadOpenedFileFactory implements OpenedFileFactory {

    public static Config defaultConfig = new Config();

    private final RangeMappedOpenedFileFactory readerFactory;

    private volatile Config config;

    @Inject
    public RollingReadOpenedFileFactory(RangeMappedOpenedFileFactory readerFactor, Config config) {
        this.readerFactory = readerFactor;
        this.config = config;
    }

    public void setConfig(Config config) {
        this.config = config;
    }

    @Override
    public OpenedFile create(File file) {
        return new RollingReadOpenedFile(file, readerFactory, config.minPageSize, config.maxPageSize);
    }

    @Override
    public void close(OpenedFile openedFile) throws Exception {
        openedFile.close();
    }

    public static class Config {

        public final int minPageSize;
        public final int maxPageSize;

        public Config() {
            minPageSize = 4 * 1024 * 1024;
            maxPageSize = 16 * 1024 * 1024;
        }

        public Config(int minPageSize, int maxPageSize) {
            this.minPageSize = minPageSize;
            this.maxPageSize = maxPageSize;
        }
    }
}
