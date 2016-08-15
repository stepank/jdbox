package jdbox.localstate;

import com.google.inject.Inject;
import jdbox.datapersist.ChangeSet;
import jdbox.datapersist.Storage;
import jdbox.driveadapter.BasicInfoProvider;
import jdbox.localstate.interfaces.*;
import jdbox.localstate.knownfiles.KnownFiles;
import jdbox.models.fileids.FileIdStore;
import jdbox.uploader.Uploader;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class LocalState {

    private final Storage storage;
    private final Uploader uploader;

    private final KnownFiles knownFiles;

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
    public LocalState(
            FileIdStore fileIdStore, BasicInfoProvider basicInfoProvider, Storage storage, Uploader uploader) {
        this.storage = storage;
        this.uploader = uploader;
        knownFiles = new KnownFiles(fileIdStore, basicInfoProvider, storage);
    }

    public void init() throws IOException {

        update(new LocalUpdate() {
            @Override
            public Void run(ChangeSet changeSet, KnownFiles knownFiles, Uploader uploader) throws IOException {

                ChangeSet ignoredChangeSet = new ChangeSet();

                knownFiles.init(ignoredChangeSet);

                return null;
            }
        });
    }

    public Long getLargestChangeId() {
        return read(new LocalReadSafe<Long>() {
            @Override
            public Long run(KnownFiles knownFiles) {
                return knownFiles.getLargestChangeId();
            }
        });
    }

    public void reset() {
        update(new LocalUpdateSafe() {
            @Override
            public void run(ChangeSet changeSet, KnownFiles knownFiles, Uploader uploader) {
                knownFiles.reset(changeSet);
            }
        });
    }

    public <T> T update(LocalUpdate<T> localUpdate) throws IOException {
        localStateLock.writeLock().lock();
        try {
            ChangeSet changeSet = new ChangeSet();
            T result = localUpdate.run(changeSet, knownFiles, uploader);
            storage.applyChangeSet(changeSet);
            return result;
        } finally {
            localStateLock.writeLock().unlock();
        }
    }

    public void update(LocalUpdateSafe localUpdate) {
        localStateLock.writeLock().lock();
        try {
            ChangeSet changeSet = new ChangeSet();
            localUpdate.run(changeSet, knownFiles, uploader);
            storage.applyChangeSet(changeSet);
        } finally {
            localStateLock.writeLock().unlock();
        }
    }

    public <T> T update(RemoteRead<T> remoteRead) throws IOException {
        uploader.pause();
        try {
            return remoteRead.run();
        } finally {
            uploader.resume();
        }
    }

    public void update(RemoteReadVoid remoteRead) throws IOException {
        uploader.pause();
        try {
            remoteRead.run();
        } finally {
            uploader.resume();
        }
    }

    public void tryUpdate(RemoteReadVoid remoteRead, int timeout, TimeUnit unit) throws IOException {

        if (!uploader.tryPause(timeout, unit))
            return;

        try {
            remoteRead.run();
        } finally {
            uploader.resume();
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
