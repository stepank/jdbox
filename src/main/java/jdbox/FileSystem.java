package jdbox;

import com.google.api.services.drive.Drive;
import com.google.common.cache.*;
import com.google.common.io.ByteStreams;
import com.google.common.util.concurrent.SettableFuture;
import net.fusejna.DirectoryFiller;
import net.fusejna.ErrorCodes;
import net.fusejna.StructFuseFileInfo;
import net.fusejna.StructStat;
import net.fusejna.types.TypeMode;
import net.fusejna.util.FuseFilesystemAdapterFull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class FileSystem extends FuseFilesystemAdapterFull {

    private static final Set<String> nonExistentFiles = new HashSet<String>() {{
        add("/.Trash");
        add("/.Trash-1000");
        add("/.xdg-volume-info");
        add("/autorun.inf");
    }};

    private static final int MAX_FILE_READERS = 10;
    private static final int FILE_READERS_EXPIRY_IN_SECS = 20;
    private static final int EXECUTOR_THREADS = 8;

    private static final Logger logger = LoggerFactory.getLogger(FileSystem.class);

    private final DriveAdapter drive;
    private final FileInfoResolver fileInfoResolver;

    private final LoadingCache<String, NavigableMap<String, File>> fileListCache;
    private final LoadingCache<File, FileReader> fileReaders;

    private final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(EXECUTOR_THREADS);

    public FileSystem(Drive drive) {

        this.drive = new DriveAdapter(drive);
        fileInfoResolver = new FileInfoResolver(this.drive);

        fileListCache = CacheBuilder.newBuilder()
                .expireAfterWrite(5, TimeUnit.SECONDS)
                .build(new CacheLoader<String, NavigableMap<String, File>>() {
                    @Override
                    public NavigableMap<String, File> load(String path) throws Exception {
                        File dir = fileInfoResolver.get(path);
                        NavigableMap<String, File> files = new TreeMap<>();
                        for (File file : FileSystem.this.drive.getChildren(dir.getId())) {
                            String name = file.getName();
                            files.put(name, file);
                            fileInfoResolver.put(
                                    path.equals(java.io.File.separator) ?
                                            path + name : path + java.io.File.separator + name,
                                    file);
                        }
                        return files;
                    }
                });

        fileReaders = CacheBuilder.newBuilder()
                .maximumSize(MAX_FILE_READERS)
                .expireAfterAccess(FILE_READERS_EXPIRY_IN_SECS, TimeUnit.SECONDS)
                .removalListener(new RemovalListener<File, FileReader>() {
                    @Override
                    public void onRemoval(RemovalNotification<File, FileReader> removalNotification) {
                        logger.debug("discarding the reader for {}", removalNotification.getKey());
                    }
                })
                .build(new CacheLoader<File, FileReader>() {
                    @Override
                    public FileReader load(File file) throws Exception {
                        logger.debug("creating a reader for {}", file);
                        return new FileReader(FileSystem.this.drive, file, executor);
                    }
                });

        executor.setRemoveOnCancelPolicy(true);
    }

    public FileInfoResolver getFileInfoResolver() {
        return fileInfoResolver;
    }

    @Override
    public int getattr(final String path, final StructStat.StatWrapper stat) {

        logger.debug("getting attrs of {}", path);

        if (nonExistentFiles.contains(path))
            return -ErrorCodes.ENOENT();

        try {

            File file = fileInfoResolver.get(path);

            if (file == null)
                return -ErrorCodes.ENOENT();

            if (file.isDirectory())
                stat.setMode(TypeMode.NodeType.DIRECTORY, true, true, true, true, true, true, true, false, true);
            else
                stat.setMode(TypeMode.NodeType.FILE);

            stat.size(file.getSize());

        } catch (Exception e) {
            logger.error("an error occured while getting attrs of {}", path, e);
        }

        return 0;
    }

    @Override
    public int readdir(final String path, final DirectoryFiller filler) {

        logger.debug("reading directory {}", path);

        try {
            for (File file : fileListCache.get(path).values()) {
                filler.add(file.getName());
            }
        } catch (Exception e) {
            logger.error("an error occured while reading directory {}", path, e);
        }

        return 0;
    }

    @Override
    public int read(String path, ByteBuffer buffer, long count, long offset, StructFuseFileInfo.FileInfoWrapper info) {

        logger.debug("reading file {}, offset {}, count {}", path, offset, count);

        try {

            File file = fileInfoResolver.get(path);

            if (!file.isDownloadable()) {
                String exportInfo = file.getExportInfo();
                buffer.put(Arrays.copyOfRange(exportInfo.getBytes(), (int) offset, (int) (offset + count)));
                return (int) Math.min(count, exportInfo.length() - offset);
            }

            long toRead = Math.min(count, file.getSize() - offset);

            fileReaders.get(file).read(buffer, offset, (int) toRead);

            return (int) toRead;

        } catch (Exception e) {
            logger.error("an error occured while reading file {}", path, e);
            return 0;
        }
    }
}

class FileReader {

    private static final int MIN_PAGE_SIZE = 4 * 1024 * 1024;
    private static final int MAX_PAGE_SIZE = 16 * 1024 * 1024;
    private static final int PAGE_EXPIRY_IN_SECS = 10;

    private static final Logger logger = LoggerFactory.getLogger(FileReader.class);

    private final DriveAdapter drive;
    private final File file;
    private final ScheduledExecutorService executor;
    private final NavigableMap<Long, Page> pages = new TreeMap<>();

    public FileReader(DriveAdapter drive, File file, ScheduledExecutorService executor) throws Exception {
        this.drive = drive;
        this.file = file;
        this.executor = executor;
    }

    public void read(ByteBuffer buffer, long offset, int count) throws Exception {

        logger.debug("reading {}, offset {}, count {}", file, offset, count);

        if (offset + count > file.getSize())
            throw new IndexOutOfBoundsException();

        synchronized (pages) {

            while (count > 0) {

                final Page page = getOrRequestPage(offset);

                logger.debug("got {} of {}", page, file);

                try {

                    int toRead = (int) Math.min(count, page.rightOffset - offset);

                    page.read(buffer, (int) (offset - page.offset), toRead);

                    if (!pages.containsKey(page.rightOffset) &&
                            page.rightOffset < file.getSize() && offset - page.offset + toRead > page.length / 2)
                        requestPage(
                                page.rightOffset, Math.min(
                                        (int) (file.getSize() - page.rightOffset),
                                        Math.min(MAX_PAGE_SIZE, page.length * 2)));

                    count -= toRead;
                    offset += toRead;

                } catch (Exception e) {
                    pages.remove(page.offset);
                    throw e;
                }
            }
        }
    }

    private Page getOrRequestPage(long offset) {

        Map.Entry<Long, Page> entry = pages.floorEntry(offset);
        if (entry == null)
            return requestPage(offset, Math.min(MIN_PAGE_SIZE, (int) (file.getSize() - offset)));

        Page page = entry.getValue();
        if (page.rightOffset > offset) {
            page.scheduleExpiry(executor);
            return page;
        }

        entry = pages.ceilingEntry(offset);
        if (entry == null)
            return requestPage(offset, Math.min(MIN_PAGE_SIZE, (int) (file.getSize() - offset)));

        return requestPage(offset, Math.min(MIN_PAGE_SIZE, (int) (entry.getValue().offset - offset)));
    }

    private Page requestPage(final long offset, final int length) {

        final Page page = new Page(file, offset, length);

        logger.debug("requesting {} of {}", page, file);

        final Date start = new Date();
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    InputStream stream = drive.downloadFileRange(file, page.offset, page.length);
                    logger.debug(
                            "got a stream of {}, offset {}, length {}, exec time {} ms",
                            FileReader.this.file, offset, length, new Date().getTime() - start.getTime());
                    page.setStream(stream);
                } catch (DriveAdapter.Exception e) {
                    page.setException(e);
                }
            }
        });

        pages.put(offset, page);
        page.scheduleExpiry(executor);

        return page;
    }

    private class Page {

        public final long offset;
        public final int length;
        public final long rightOffset;

        private final File file;
        private final SettableFuture<InputStream> stream;
        private final byte[] buffer;
        private int read = 0;
        private ScheduledFuture expiry;

        private Page(File file, long offset, int length) {
            this.file = file;
            this.offset = offset;
            this.length = length;
            rightOffset = offset + length;
            stream = SettableFuture.create();
            buffer = new byte[length];
        }

        public void setStream(InputStream stream) {
            this.stream.set(stream);
        }

        public void setException(Exception e) {
            this.stream.setException(e);
        }

        public void read(ByteBuffer buffer, int offset, int count) throws Exception {

            logger.debug("reading {} of {}, offset {}, count {}", this, file, offset, count);

            if (offset + count > rightOffset)
                throw new IndexOutOfBoundsException();

            if (offset + count > read) {
                InputStream stream = this.stream.get();
                ByteStreams.readFully(stream, this.buffer, read, offset + count - read);
                read = offset + count;
            }

            buffer.put(this.buffer, offset, count);

            logger.debug("done reading {} of {}, offset {}, count {}", this, file, offset, count);
        }

        public void scheduleExpiry(ScheduledExecutorService executor) {

            if (expiry != null)
                expiry.cancel(false);

            final long offset = this.offset;

            logger.debug("scheduling {} to be discarded", this);
            expiry = executor.schedule(new Runnable() {
                @Override
                public void run() {
                    logger.debug("discarding {} of {}", Page.this, file);
                    synchronized (pages) {
                        pages.remove(offset);
                    }
                }
            }, PAGE_EXPIRY_IN_SECS, TimeUnit.SECONDS);
        }

        public String toString() {
            return String.format("page offset=%s length=%s", offset, length);
        }
    }
}
