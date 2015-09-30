package jdbox.openedfiles;

import com.google.inject.Inject;
import jdbox.driveadapter.DriveAdapter;
import jdbox.driveadapter.Field;
import jdbox.models.File;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executor;

class RollingReadOpenedFile implements ByteStore {

    private static final int PAGES_NUMBER = 3;
    private static final double MAX_STRETCH_FACTOR = 1.5;

    private static final Logger logger = LoggerFactory.getLogger(RollingReadOpenedFile.class);

    private final File file;
    private final jdbox.driveadapter.File daFile;
    private final DriveAdapter drive;
    private final StreamCachingByteSourceFactory readerFactory;
    private final int minPageSize;
    private final int maxPageSize;
    private final Readers readers = new Readers(PAGES_NUMBER);
    private final Executor executor;

    private boolean closed = false;

    RollingReadOpenedFile(
            File file, DriveAdapter drive, StreamCachingByteSourceFactory readerFactory,
            int minPageSize, int maxPageSize, Executor executor) {
        this.file = file;
        this.daFile = file.toDaFile(EnumSet.of(Field.DOWNLOAD_URL));
        this.drive = drive;
        this.readerFactory = readerFactory;
        this.minPageSize = minPageSize;
        this.maxPageSize = maxPageSize;
        this.executor = executor;
    }

    @Override
    public synchronized int read(ByteBuffer buffer, long offset, int count) throws IOException {

        logger.debug("reading {}, offset {}, count {}", file, offset, count);

        assert !closed;
        assert offset < file.getSize();

        count = (int) Math.min(count, file.getSize() - offset);

        int read = 0;

        while (read < count) {

            Readers.Entry entry = getReaderAndCreateAhead(offset + read);

            logger.debug("got {}", entry.reader);

            try {
                int bytesToRead = (int) Math.min(count - read, entry.rightOffset - offset - read);
                read += entry.reader.read(buffer, (int) (offset + read - entry.offset), bytesToRead);
            } catch (IOException e) {
                readers.remove(entry);
                throw e;
            }
        }

        logger.debug("done reading {}, offset {}, count {}", file, offset, count);

        return read;
    }

    @Override
    public int write(ByteBuffer buffer, long offset, int count) throws IOException {
        throw new UnsupportedOperationException("write is not supported");
    }

    @Override
    public void truncate(long offset) throws IOException {
        throw new UnsupportedOperationException("truncate is not supported");
    }

    public synchronized void close() throws IOException {

        if (closed)
            return;

        closed = true;

        readers.closeAll();
    }

    private Readers.Entry getReaderAndCreateAhead(long offset) {

        Readers.Entry entry = getOrCreateReader(offset);

        if (entry.rightOffset == file.getSize())
            return entry;

        Readers.Entry nextEntry = readers.ceiling(entry.rightOffset);
        if (nextEntry == null || entry.rightOffset < nextEntry.offset) {
            int desiredLength = Integer.highestOneBit(entry.length);
            if (desiredLength < maxPageSize)
                desiredLength *= 2;
            long rightBoundary = nextEntry == null ? file.getSize() : nextEntry.offset;
            createReader(entry.rightOffset, desiredLength, (int) (rightBoundary - entry.rightOffset));
        }

        return entry;
    }

    private Readers.Entry getOrCreateReader(long offset) {

        Readers.Entry floorEntry = readers.floor(offset);
        if (floorEntry != null && floorEntry.rightOffset > offset)
            return floorEntry.touch();

        Readers.Entry ceilingEntry = readers.ceiling(offset);
        if (ceilingEntry == null)
            return createReader(offset, minPageSize, (int) (file.getSize() - offset));

        return createReader(offset, minPageSize, (int) (ceilingEntry.offset - offset));
    }

    private Readers.Entry createReader(long offset, int desiredLength, int maxLength) {
        int length = (desiredLength * MAX_STRETCH_FACTOR > maxLength) ? maxLength : desiredLength;
        return readers.create(offset, length);
    }

    public String toString() {
        return String.format("RollingReadOpenedFile{file = %s}", file.getName());
    }

    private class Readers {

        private final int maxSize;

        private List<Entry> entries = new LinkedList<>();
        private long current = 0;

        public Readers(int maxSize) {
            this.maxSize = maxSize;
        }

        public Entry floor(long offset) {
            Entry result = null;
            for (Entry entry : entries) {
                if (entry.offset <= offset && (result == null || entry.offset > result.offset))
                    result = entry;
            }
            return result;
        }

        public Entry ceiling(long offset) {
            Entry result = null;
            for (Entry entry : entries) {
                if (entry.offset >= offset && (result == null || entry.offset < result.offset))
                    result = entry;
            }
            return result;
        }

        public Entry create(long offset, int length) {

            Entry result = new Entry(
                    readerFactory.create(drive.downloadFileRangeAsync(daFile, offset, length, executor)),
                    offset, length, current++);

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
                } catch (IOException e) {
                    logger.error("an error occured while closing an evicted reader", e);
                }
            }

            return result;
        }

        public void remove(Readers.Entry entry) {
            for (Iterator<Entry> iterator = entries.iterator(); iterator.hasNext(); ) {
                if (iterator.next() == entry) {
                    iterator.remove();
                    break;
                }
            }
        }

        public void closeAll() throws IOException {
            for (Entry entry : entries) {
                entry.reader.close();
            }
            entries.clear();
            entries = null;
        }

        private class Entry {

            final ByteSource reader;
            final long offset;
            final long rightOffset;
            final int length;
            long touched;

            Entry(ByteSource reader, long offset, int length, long touched) {
                this.reader = reader;
                this.offset = offset;
                this.rightOffset = offset + length;
                this.length = length;
                this.touched = touched;
            }

            public Entry touch() {
                touched = current++;
                return this;
            }
        }
    }
}

class RollingReadOpenedFileFactory implements ByteStoreFactory {

    public static Config defaultConfig = new Config();

    private final DriveAdapter drive;
    private final StreamCachingByteSourceFactory readerFactory;
    private final Executor executor;

    private volatile Config config;

    @Inject
    public RollingReadOpenedFileFactory(
            DriveAdapter drive, StreamCachingByteSourceFactory readerFactory,
            @PackagePrivate Executor executor, Config config) {
        this.drive = drive;
        this.readerFactory = readerFactory;
        this.executor = executor;
        this.config = config;
    }

    public void setConfig(Config config) {
        this.config = config;
    }

    @Override
    public long getSize(File file) {
        return file.getSize();
    }

    @Override
    public RollingReadOpenedFile create(File file) {
        return new RollingReadOpenedFile(file, drive, readerFactory, config.minPageSize, config.maxPageSize, executor);
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
