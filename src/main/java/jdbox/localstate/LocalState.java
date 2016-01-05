package jdbox.localstate;

import com.google.inject.Inject;
import jdbox.localstate.knownfiles.KnownFiles;
import jdbox.models.fileids.FileIdStore;
import jdbox.uploader.Uploader;

import java.io.IOException;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class LocalState {

    private final FileIdStore fileIdStore;
    private final Uploader uploader;

    private final KnownFiles knownFiles = new KnownFiles();

    // Read lock is acquired for reading from knownFiles to prevent modification of its state
    // while read operations are in progress.
    // Write lock is acquired on:
    // 1. knownFiles modifications. This ensures consistent modification of its state.
    // 1. FileTree public write operations. This ensures consistent modification of knownFiles and
    //    correct order of operations submitted to the Uploader.
    // 2. Retrieval of the list of files in a directory. The primary goal is to prevent concurrent retrieval of
    //    the list of files in one directory. Although this also prevents any other write & read operations
    //    on the FileTree, it is not considered a problem at the moment, because:
    //      a. Directory contents are cached and retrieval do not seem to be frequent.
    //      b. Implementing more fine-grained locking would require considerable effort.
    private final ReadWriteLock localStateLock = new ReentrantReadWriteLock();

    @Inject
    public LocalState(FileIdStore fileIdStore, Uploader uploader) {
        this.fileIdStore = fileIdStore;
        this.uploader = uploader;
    }

    public void setRoot(final String rootId) {
        update(new LocalUpdateSafe() {
            @Override
            public Void run(KnownFiles knownFiles, Uploader uploader) {
                knownFiles.setRoot(fileIdStore.get(rootId));
                return null;
            }
        });
    }

    public void reset() {
        update(new LocalUpdateSafe() {
            @Override
            public Void run(KnownFiles knownFiles, Uploader uploader) {
                knownFiles.setRoot(knownFiles.getRoot().getId());
                return null;
            }
        });
    }

    public <T> T update(LocalUpdate<T> localUpdate) throws IOException {
        localStateLock.writeLock().lock();
        try {
            return localUpdate.run(knownFiles, uploader);
        } finally {
            localStateLock.writeLock().unlock();
        }
    }

    public <T> T update(LocalUpdateSafe<T> localUpdate) {
        localStateLock.writeLock().lock();
        try {
            return localUpdate.run(knownFiles, uploader);
        } finally {
            localStateLock.writeLock().unlock();
        }
    }

    public <T> T read(LocalRead<T> localRead) throws IOException {
        localStateLock.readLock().lock();
        try {
            return localRead.run(knownFiles);
        } finally {
            localStateLock.readLock().unlock();
        }
    }

    public <T> T read(LocalReadSafe<T> localRead) {
        localStateLock.readLock().lock();
        try {
            return localRead.run(knownFiles);
        } finally {
            localStateLock.readLock().unlock();
        }
    }
}
