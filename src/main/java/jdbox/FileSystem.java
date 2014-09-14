package jdbox;

import com.google.api.services.drive.Drive;
import net.fusejna.DirectoryFiller;
import net.fusejna.ErrorCodes;
import net.fusejna.StructFuseFileInfo;
import net.fusejna.StructStat;
import net.fusejna.types.TypeMode;
import net.fusejna.util.FuseFilesystemAdapterFull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class FileSystem extends FuseFilesystemAdapterFull {

    private static final int CACHE_TTL = 5000;
    private static final Set<String> nonExistentFiles = new HashSet<String>() {{
        add("/.Trash");
        add("/.Trash-1000");
        add("/.xdg-volume-info");
        add("/autorun.inf");
    }};

    private static final Logger logger = LoggerFactory.getLogger(FileSystem.class);

    private final DriveAdapter drive;
    private final FileInfoResolver fileInfoResolver;

    private final Map<String, CacheEntry> cache = new HashMap<>();
    private final Lock cacheWriteLock;
    private final Lock cacheReadLock;

    public FileSystem(Drive drive) {

        this.drive = new DriveAdapter(drive);
        fileInfoResolver = new FileInfoResolver(this.drive);

        ReadWriteLock cacheLock = new ReentrantReadWriteLock();
        this.cacheWriteLock = cacheLock.writeLock();
        this.cacheReadLock = cacheLock.readLock();
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
            logger.error("an error occured while getting attrs", e);
        }

        return 0;
    }

    private boolean cacheIsValid(String path) {
        CacheEntry entry = cache.get(path);
        return entry != null && new Date().getTime() - entry.requestDate.getTime() < CACHE_TTL;
    }

    @Override
    public int readdir(final String path, final DirectoryFiller filler) {

        logger.debug("reading directory {}", path);

        cacheReadLock.lock();

        if (!cacheIsValid(path)) {

            cacheReadLock.unlock();
            cacheWriteLock.lock();

            try {

                if (!cacheIsValid(path)) {
                    NavigableMap<String, File> files = new TreeMap<>();
                    cache.put(path, new CacheEntry(files));
                    for (File file : drive.getChildren(fileInfoResolver.get(path).getId())) {
                        String name = file.getName();
                        files.put(name, file);
                        fileInfoResolver.put(
                                path.equals(java.io.File.separator) ?
                                        path + name : path + java.io.File.separator + name,
                                file);
                    }
                }

                cacheReadLock.lock();

            } catch (Exception e) {
                logger.error("an error occured while reading directory to cache", e);
            } finally {
                cacheWriteLock.unlock();
            }
        }

        try {
            CacheEntry entry = cache.get(path);
            if (entry != null) {
                for (File file : cache.get(path).files.values()) {
                    filler.add(file.getName());
                }
            }
        } catch (Exception e) {
            logger.error("an error occured while reading directory from cache", e);
        } finally {
            cacheReadLock.unlock();
        }

        return 0;
    }

    @Override
    public int read(String path, ByteBuffer buffer, long count, long offset, StructFuseFileInfo.FileInfoWrapper info) {

        logger.debug("reading file {}, offset {}, count {}", path, offset, count);

        try {
            return drive.downloadFileRange(fileInfoResolver.get(path), buffer, offset, count);
        } catch (Exception e) {
            logger.error("an error occured while reading file", e);
            return 0;
        }
    }

    private class CacheEntry {

        public final Date requestDate;
        public final NavigableMap<String, File> files;

        public CacheEntry(NavigableMap<String, File> files) {
            this(new Date(), files);
        }

        public CacheEntry(Date requestDate, NavigableMap<String, File> files) {
            this.requestDate = requestDate;
            this.files = files;
        }
    }
}