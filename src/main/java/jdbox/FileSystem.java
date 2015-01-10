package jdbox;

import com.google.inject.Inject;
import jdbox.filereaders.FileReader;
import jdbox.filereaders.FileReaderFactory;
import jdbox.filetree.File;
import jdbox.filetree.FileTree;
import net.fusejna.*;
import net.fusejna.types.TypeMode;
import net.fusejna.util.FuseFilesystemAdapterFull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicLong;

public class FileSystem extends FuseFilesystemAdapterFull {

    private static final Logger logger = LoggerFactory.getLogger(FileSystem.class);

    private final DriveAdapter drive;
    private final FileTree fileTree;
    private final ScheduledExecutorService executor;

    private final Map<Long, FileReader> fileReaders = new ConcurrentHashMap<>();
    private final AtomicLong currentFileHandler = new AtomicLong();

    @Inject
    public FileSystem(DriveAdapter drive, FileTree fileTree, final ScheduledExecutorService executor) {
        this.drive = drive;
        this.fileTree = fileTree;
        this.executor = executor;
    }

    public int getFileReadersCount() {
        return fileReaders.size();
    }

    public long getCurrentFileHandler() {
        return currentFileHandler.get();
    }

    @Override
    public int getattr(final String path, final StructStat.StatWrapper stat) {

        logger.debug("[{}] getting attrs", path);

        try {

            File file = fileTree.get(path);

            if (file.isDirectory())
                stat.setMode(TypeMode.NodeType.DIRECTORY, true, true, true, true, true, true, true, false, true);
            else
                stat.setMode(TypeMode.NodeType.FILE).size(file.getSize());

            stat.size(file.getSize());
            if (file.getCreatedDate() != null)
                stat.ctime(file.getCreatedDate().getTime() / 1000);
            if (file.getModifiedDate() != null)
                stat.mtime(file.getModifiedDate().getTime() / 1000);
            if (file.getAccessedDate() != null)
                stat.atime(file.getAccessedDate().getTime() / 1000);

            return 0;

        } catch (FileTree.NoSuchFileException e) {
            return -ErrorCodes.ENOENT();
        } catch (Exception e) {
            logger.error("[{}] an error occured while getting attrs", path, e);
            return -ErrorCodes.EPIPE();
        }
    }

    @Override
    public int readdir(final String path, final DirectoryFiller filler) {

        logger.debug("[{}] reading directory", path);

        try {

            for (File file : fileTree.getChildren(path).values()) {
                filler.add(file.getName());
            }

            return 0;

        } catch (FileTree.NoSuchFileException e) {
            return -ErrorCodes.ENOENT();
        } catch (FileTree.NotDirectoryException e) {
            return -ErrorCodes.ENOTDIR();
        } catch (Exception e) {
            logger.error("[{}] an error occured while reading directory", path, e);
            return -ErrorCodes.EPIPE();
        }
    }

    @Override
    public int open(String path, StructFuseFileInfo.FileInfoWrapper info) {

        info.fh(currentFileHandler.incrementAndGet());

        logger.debug("[{}] opening file, fh {}", path, info.fh());

        try {

            File file = fileTree.get(path);

            if (file.isDownloadable())
                fileReaders.put(info.fh(), FileReaderFactory.create(file, FileSystem.this.drive, executor));

            return 0;

        } catch (FileTree.NoSuchFileException e) {
            return -ErrorCodes.ENOENT();
        } catch (Exception e) {
            logger.error("[{}] an error occured while opening file", path, e);
            return -ErrorCodes.EPIPE();
        }
    }

    @Override
    public int release(String path, StructFuseFileInfo.FileInfoWrapper info) {

        logger.debug("[{}] releasing file, fh {}", path, info.fh_old());

        try {

            FileReader fr = fileReaders.remove(info.fh());

            if (fr != null)
                fr.discard();

            return 0;

        } catch (Exception e) {
            logger.error("[{}] an error occured while releasig file", path, e);
            return -ErrorCodes.EPIPE();
        }
    }

    @Override
    public int read(String path, ByteBuffer buffer, long count, long offset, StructFuseFileInfo.FileInfoWrapper info) {

        logger.debug("[{}] reading file, fh {}, offset {}, count {}", path, info.fh_old(), offset, count);

        try {

            File file = fileTree.get(path);

            if (!file.isDownloadable()) {
                String exportInfo = file.getExportInfo();
                buffer.put(Arrays.copyOfRange(exportInfo.getBytes(), (int) offset, (int) (offset + count)));
                return (int) Math.min(count, exportInfo.length() - offset);
            }

            long toRead = Math.min(count, file.getSize() - offset);

            fileReaders.get(info.fh()).read(buffer, offset, (int) toRead);

            return (int) toRead;

        } catch (FileTree.NoSuchFileException e) {
            return -ErrorCodes.ENOENT();
        } catch (Exception e) {
            logger.error("[{}] an error occured while reading file", path, e);
            return -ErrorCodes.EPIPE();
        }
    }

    @Override
    public int create(String path, TypeMode.ModeWrapper mode, StructFuseFileInfo.FileInfoWrapper info) {

        logger.debug("[{}] creating file", path);

        if (mode.type() != TypeMode.NodeType.FILE)
            return -ErrorCodes.ENOSYS();

        try {
            fileTree.create(path, false);
            return 0;
        } catch (FileTree.NoSuchFileException e) {
            return -ErrorCodes.ENOENT();
        } catch (Exception e) {
            logger.error("[{}] an error occured while creating file", path, e);
            return -ErrorCodes.EPIPE();
        }
    }

    @Override
    public int mkdir(String path, TypeMode.ModeWrapper mode) {

        logger.debug("[{}] creating directory", path);

        try {
            fileTree.create(path, true);
            return 0;
        } catch (FileTree.NoSuchFileException e) {
            return -ErrorCodes.ENOENT();
        } catch (Exception e) {
            logger.error("[{}] an error occured while creating file", path, e);
            return -ErrorCodes.EPIPE();
        }
    }

    @Override
    public int utimens(String path, StructTimeBuffer.TimeBufferWrapper wrapper) {

        logger.debug("[{}] setting times", path);

        try {
            fileTree.setDates(path, new Date(wrapper.ac_sec() * 1000), new Date(wrapper.mod_sec() * 1000));
            return 0;
        } catch (FileTree.NoSuchFileException e) {
            return -ErrorCodes.ENOENT();
        } catch (Exception e) {
            logger.error("[{}] an error occured while setting times", path, e);
            return -ErrorCodes.EPIPE();
        }
    }
}
