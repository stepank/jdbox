package jdbox;

import com.google.inject.Inject;
import jdbox.content.OpenedFiles;
import jdbox.filetree.FileTree;
import jdbox.models.File;
import net.fusejna.*;
import net.fusejna.types.TypeMode;
import net.fusejna.util.FuseFilesystemAdapterFull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
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
    public void beforeMount(java.io.File mountPoint) {
        logger.debug("mounting to {}", mountPoint);
        super.beforeMount(mountPoint);
    }

    @Override
    public int getattr(final String path, final StructStat.StatWrapper stat) {

        OperationContext.initialize(path, "getattr");

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
                        true, openedFiles.isWritable(file), false,
                        true, false, false,
                        true, false, false);

            stat.size(openedFiles.getSize(file));

            if (file.getCreatedDate() != null)
                stat.ctime(file.getCreatedDate().getTime() / 1000);
            if (file.getModifiedDate() != null)
                stat.mtime(file.getModifiedDate().getTime() / 1000);
            if (file.getAccessedDate() != null)
                stat.atime(file.getAccessedDate().getTime() / 1000);

            return 0;

        } catch (FileTree.NoSuchFileException e) {
            return -ErrorCodes.ENOENT();
        } catch (IOException e) {
            logger.error("an error occured while getting attrs", e);
            return -ErrorCodes.EPIPE();
        } finally {
            OperationContext.clear();
        }
    }

    @Override
    public int readdir(final String path, final DirectoryFiller filler) {

        OperationContext.initialize(path, "readdir");

        try {

            filler.add(fileTree.getChildren(path));

            return 0;

        } catch (FileTree.NoSuchFileException e) {
            return -ErrorCodes.ENOENT();
        } catch (FileTree.NotDirectoryException e) {
            return -ErrorCodes.ENOTDIR();
        } catch (IOException e) {
            logger.error("an error occured while reading directory", e);
            return -ErrorCodes.EPIPE();
        } finally {
            OperationContext.clear();
        }
    }

    @Override
    public int open(String path, StructFuseFileInfo.FileInfoWrapper info) {

        OperationContext.initialize(path, "open", "mode {}", info.openMode());

        try {
            info.fh(openedFiles.open(fileTree.get(path), getOpenMode(info.openMode())).handler);
            logger.debug("opened file, fh {}, mode {}", info.fh(), info.openMode());
            return 0;
        } catch (FileTree.NoSuchFileException e) {
            return -ErrorCodes.ENOENT();
        } catch (IOException e) {
            logger.error("an error occured while opening file", e);
            return -ErrorCodes.EPIPE();
        } finally {
            OperationContext.clear();
        }
    }

    @Override
    public int release(String path, StructFuseFileInfo.FileInfoWrapper info) {

        OperationContext.initialize(path, "release", "fh {}", info.fh_old());

        try {

            openedFiles.get(info.fh()).close();

            return 0;

        } catch (IOException e) {
            logger.error("an error occured while releasig file", e);
            return -ErrorCodes.EPIPE();
        } finally {
            OperationContext.clear();
        }
    }

    @Override
    public int read(String path, ByteBuffer buffer, long count, long offset, StructFuseFileInfo.FileInfoWrapper info) {

        OperationContext.initialize(path, "read", "fh {}, offset {}, count {}", info.fh(), offset, count);

        try {
            return openedFiles.get(info.fh()).read(buffer, offset, (int) count);
        } catch (IOException e) {
            logger.error("an error occured while reading file", e);
            return -ErrorCodes.EPIPE();
        } finally {
            OperationContext.clear();
        }
    }

    @Override
    public int write(String path, ByteBuffer buffer, long count, long offset, StructFuseFileInfo.FileInfoWrapper info) {

        OperationContext.initialize(path, "write", "fh {}, offset {}, count {}", info.fh(), offset, count);

        try {
            return openedFiles.get(info.fh()).write(buffer, offset, (int) count);
        } catch (IOException e) {
            logger.error("an error occured while writing file", e);
            return -ErrorCodes.EPIPE();
        } finally {
            OperationContext.clear();
        }
    }

    @Override
    public int create(String path, TypeMode.ModeWrapper mode, StructFuseFileInfo.FileInfoWrapper info) {

        OperationContext.initialize(path, "create");

        if (mode.type() != TypeMode.NodeType.FILE)
            return -ErrorCodes.ENOSYS();

        try {
            info.fh(openedFiles.open(fileTree.create(path, false), getOpenMode(info.openMode())).handler);
            logger.debug("opened file, fh {}, mode {}", info.fh(), info.openMode());
            return 0;
        } catch (FileTree.NoSuchFileException e) {
            return -ErrorCodes.ENOENT();
        } catch (IOException e) {
            logger.error("an error occured while creating file", e);
            return -ErrorCodes.EPIPE();
        } finally {
            OperationContext.clear();
        }
    }

    @Override
    public int mkdir(String path, TypeMode.ModeWrapper mode) {

        OperationContext.initialize(path, "mkdir");

        try {
            fileTree.create(path, true);
            return 0;
        } catch (FileTree.NoSuchFileException e) {
            return -ErrorCodes.ENOENT();
        } catch (IOException e) {
            logger.error("an error occured while creating file", e);
            return -ErrorCodes.EPIPE();
        } finally {
            OperationContext.clear();
        }
    }

    @Override
    public int truncate(String path, long offset) {

        OperationContext.initialize(path, "truncate", "offset {}", offset);

        try {
            try (OpenedFiles.FileHandlerRemovingProxyByteStore openedFile =
                         openedFiles.open(fileTree.get(path), OpenedFiles.OpenMode.WRITE_ONLY)) {
                logger.debug(
                        "opened file for truncate, fh {}, mode {}",
                        path, openedFile.handler, OpenedFiles.OpenMode.WRITE_ONLY);
                openedFile.truncate(offset);
            }
            return 0;
        } catch (FileTree.NoSuchFileException e) {
            return -ErrorCodes.ENOENT();
        } catch (IOException e) {
            logger.error("an error occured while opening file", e);
            return -ErrorCodes.EPIPE();
        } finally {
            OperationContext.clear();
        }
    }

    @Override
    public int utimens(String path, StructTimeBuffer.TimeBufferWrapper wrapper) {

        OperationContext.initialize(path, "utimens");

        try {
            fileTree.setDates(path, new Date(wrapper.mod_sec() * 1000), new Date(wrapper.ac_sec() * 1000));
            return 0;
        } catch (FileTree.NoSuchFileException e) {
            return -ErrorCodes.ENOENT();
        } catch (IOException e) {
            logger.error("an error occured while setting times", e);
            return -ErrorCodes.EPIPE();
        } finally {
            OperationContext.clear();
        }
    }

    @Override
    public int unlink(String path) {

        OperationContext.initialize(path, "unlink");

        try {
            fileTree.remove(path);
            return 0;
        } catch (FileTree.NoSuchFileException e) {
            return -ErrorCodes.ENOENT();
        } catch (FileTree.NonEmptyDirectoryException e) {
            return -ErrorCodes.ENOTEMPTY();
        } catch (FileTree.AccessDeniedException e) {
            return -ErrorCodes.EACCES();
        } catch (IOException e) {
            logger.error("an error occured while removing file", e);
            return -ErrorCodes.EPIPE();
        } finally {
            OperationContext.clear();
        }
    }

    @Override
    public int rmdir(String path) {
        return unlink(path);
    }

    @Override
    public int rename(String path, String newPath) {

        OperationContext.initialize(path, "rename", "to {}", newPath);

        try {
            fileTree.move(path, newPath);
            return 0;
        } catch (FileTree.NoSuchFileException e) {
            return -ErrorCodes.ENOENT();
        } catch (IOException e) {
            logger.error("an error occured while moving file", e);
            return -ErrorCodes.EPIPE();
        } finally {
            OperationContext.clear();
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
