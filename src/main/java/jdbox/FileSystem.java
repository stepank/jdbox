package jdbox;

import com.google.inject.Inject;
import jdbox.filetree.File;
import jdbox.filetree.FileTree;
import jdbox.openedfiles.OpenedFiles;
import net.fusejna.*;
import net.fusejna.types.TypeMode;
import net.fusejna.util.FuseFilesystemAdapterFull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.Date;

public class FileSystem extends FuseFilesystemAdapterFull {

    private static final Logger logger = LoggerFactory.getLogger(FileSystem.class);

    private final FileTree fileTree;
    private final OpenedFiles openedFiles;

    @Inject
    public FileSystem(FileTree fileTree, OpenedFiles openedFiles) {
        this.fileTree = fileTree;
        this.openedFiles = openedFiles;
    }

    @Override
    public int getattr(final String path, final StructStat.StatWrapper stat) {

        logger.debug("[{}] getting attrs", path);

        try {

            File file = fileTree.get(path);

            if (file.isDirectory())
                stat.setMode(
                        TypeMode.NodeType.DIRECTORY,
                        true, true, true,
                        true, false, true,
                        true, false, true);
            else
                stat.setMode(
                        TypeMode.NodeType.FILE,
                        true, file.isReal(), false,
                        true, false, false,
                        true, false, false).size(file.getSize());

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

        logger.debug("[{}] opening, mode {}", path, info.openMode());

        try {
            info.fh(openedFiles.open(fileTree.get(path), getOpenMode(info.openMode())));
            logger.debug("[{}] opened file, fh {}, mode {}", path, info.fh(), info.openMode());
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

        logger.debug("[{}] releasing, fh {}", path, info.fh_old());

        try {
            openedFiles.close(info.fh());
            return 0;
        } catch (Exception e) {
            logger.error("[{}] an error occured while releasig file", path, e);
            return -ErrorCodes.EPIPE();
        }
    }

    @Override
    public int read(String path, ByteBuffer buffer, long count, long offset, StructFuseFileInfo.FileInfoWrapper info) {

        logger.debug("[{}] reading, fh {}, offset {}, count {}", path, info.fh(), offset, count);

        try {
            return openedFiles.get(info.fh()).read(buffer, offset, (int) count);
        } catch (FileTree.NoSuchFileException e) {
            return -ErrorCodes.ENOENT();
        } catch (Exception e) {
            logger.error("[{}] an error occured while reading file", path, e);
            return -ErrorCodes.EPIPE();
        }
    }

    @Override
    public int write(String path, ByteBuffer buffer, long count, long offset, StructFuseFileInfo.FileInfoWrapper info) {

        logger.debug("[{}] writing, fh {}, offset {}, count {}", path, info.fh(), offset, count);

        try {
            return openedFiles.get(info.fh()).write(buffer, offset, (int) count);
        } catch (FileTree.NoSuchFileException e) {
            return -ErrorCodes.ENOENT();
        } catch (Exception e) {
            logger.error("[{}] an error occured while writing file", path, e);
            return -ErrorCodes.EPIPE();
        }
    }

    @Override
    public int create(String path, TypeMode.ModeWrapper mode, StructFuseFileInfo.FileInfoWrapper info) {

        logger.debug("[{}] creating file", path);

        if (mode.type() != TypeMode.NodeType.FILE)
            return -ErrorCodes.ENOSYS();

        try {
            info.fh(openedFiles.open(fileTree.create(path, false), getOpenMode(info.openMode())));
            logger.debug("[{}] opened file, fh {}, mode {}", path, info.fh(), info.openMode());
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

        if (mode.type() != TypeMode.NodeType.DIRECTORY)
            return -ErrorCodes.ENOSYS();

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
    public int truncate(String path, long offset) {

        logger.debug("[{}] truncating, offset {}", path, offset);

        long fileHandler = 0;
        try {
            fileHandler = openedFiles.open(fileTree.get(path), OpenedFiles.OpenMode.WRITE_ONLY);
            logger.debug("[{}] opened file, fh {}, mode {}", path, fileHandler, OpenedFiles.OpenMode.WRITE_ONLY);
            return 0;
        } catch (FileTree.NoSuchFileException e) {
            return -ErrorCodes.ENOENT();
        } catch (Exception e) {
            logger.error("[{}] an error occured while opening file", path, e);
            return -ErrorCodes.EPIPE();
        } finally {
            if (fileHandler != 0)
                try {
                    openedFiles.close(fileHandler);
                } catch (Exception e) {
                    logger.error("[{}] an error occured while closing file", path, e);
                }
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

    private static OpenedFiles.OpenMode getOpenMode(StructFuseFileInfo.FileInfoWrapper.OpenMode openMode) {
        switch (openMode) {
            case READONLY:
                return OpenedFiles.OpenMode.READ_ONLY;
            case WRITEONLY:
                return OpenedFiles.OpenMode.WRITE_ONLY;
            case READWRITE:
                return OpenedFiles.OpenMode.READ_WRITE;
            default:
                return null;
        }
    }
}
