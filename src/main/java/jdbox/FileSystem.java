package jdbox;

import com.google.api.services.drive.Drive;
import com.google.common.cache.*;
import com.google.common.io.ByteStreams;
import com.google.common.util.concurrent.SettableFuture;
import jdbox.filereaders.FileReader;
import jdbox.filereaders.FileReaderFactory;
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
                        logger.debug("discarding {}", removalNotification.getValue());
                    }
                })
                .build(new CacheLoader<File, FileReader>() {
                    @Override
                    public FileReader load(File file) throws Exception {
                        logger.debug("creating a reader for {}", file);
                        return FileReaderFactory.create(file, FileSystem.this.drive, executor);
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
