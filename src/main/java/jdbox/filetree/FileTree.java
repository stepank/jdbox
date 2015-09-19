package jdbox.filetree;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import jdbox.driveadapter.DriveAdapter;
import jdbox.filetree.knownfiles.KnownFile;
import jdbox.filetree.knownfiles.KnownFiles;
import jdbox.models.File;
import jdbox.models.fileids.FileId;
import jdbox.models.fileids.FileIdStore;
import jdbox.openedfiles.FileSizeUpdateEvent;
import jdbox.openedfiles.OpenedFilesManager;
import jdbox.uploader.Task;
import jdbox.uploader.UploadFailureEvent;
import jdbox.uploader.Uploader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Subscription;
import rx.functions.Action1;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class FileTree {

    public static final String uploadNotificationFileName = "READ ME - UPLOAD IS BROKEN.txt";

    private static final Logger logger = LoggerFactory.getLogger(FileTree.class);

    private final DriveAdapter drive;
    private final FileIdStore fileIdStore;
    private final rx.Observable<FileSizeUpdateEvent> fileSizeUpdateEvent;
    private final rx.Observable<UploadFailureEvent> uploadFailureEvent;
    private final OpenedFilesManager openedFilesManager;
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

    // Read lock is acquired on upload operations that require feedback to prevent local state's modifications
    // that can be introduced by retrieval of data from the cloud. Correctness of order of write operations
    // is ensured by correctness of order of operations submitted to the Uploader (see above) and the Uploader itself.
    // Write lock is acquired on any retrieval of data from the cloud to ensure consistent changes to the local state.
    private final ReadWriteLock syncWithRemoteLock = new ReentrantReadWriteLock();

    private volatile ScheduledExecutorService scheduler;
    private volatile Subscription fileSizeUpdateEventSubscription;
    private volatile Subscription uploadFailureEventSubscription;
    private volatile long largestChangeId;

    private volatile KnownFile uploadFailureNotificationFile;

    private final static Getter<List<String>> namesGetter = new Getter<List<String>>() {
        @Override
        public List<String> apply(String fileName, final Map<String, KnownFile> files) {
            return ImmutableList.copyOf(
                    Collections2.transform(files.entrySet(), new Function<Map.Entry<String, KnownFile>, String>() {
                        @Nullable
                        @Override
                        public String apply(Map.Entry<String, KnownFile> entry) {
                            return entry.getKey();
                        }
                    }));
        }
    };

    private final static Getter<File> singleFileGetter = new Getter<File>() {
        @Override
        public File apply(String fileName, Map<String, KnownFile> files) {
            KnownFile kf = singleKnownFileGetter.apply(fileName, files);
            return kf != null ? kf.toFile() : null;
        }
    };

    private final static Getter<KnownFile> singleKnownFileGetter = new Getter<KnownFile>() {
        @Override
        public KnownFile apply(String fileName, Map<String, KnownFile> files) {
            return files.get(fileName);
        }
    };

    @Inject
    public FileTree(
            DriveAdapter drive, FileIdStore fileIdStore,
            Observable<FileSizeUpdateEvent> fileSizeUpdateEvent, Observable<UploadFailureEvent> uploadFailureEvent,
            OpenedFilesManager openedFilesManager, Uploader uploader) {

        this.drive = drive;
        this.fileIdStore = fileIdStore;
        this.fileSizeUpdateEvent = fileSizeUpdateEvent;
        this.uploadFailureEvent = uploadFailureEvent;
        this.openedFilesManager = openedFilesManager;
        this.uploader = uploader;
    }

    public int getKnownFileCount() {
        localStateLock.readLock().lock();
        try {
            return knownFiles.getFileCount();
        } finally {
            localStateLock.readLock().unlock();
        }
    }

    public int getTrackedDirCount() {
        localStateLock.readLock().lock();
        try {
            return knownFiles.getTrackedDirCount();
        } finally {
            localStateLock.readLock().unlock();
        }
    }

    public void init() throws Exception {

        fileSizeUpdateEventSubscription = fileSizeUpdateEvent.subscribe(new Action1<FileSizeUpdateEvent>() {
            @Override
            public void call(FileSizeUpdateEvent event) {
                updateFileSize(event.fileId, event.fileSize);
            }
        });

        uploadFailureEventSubscription = uploadFailureEvent.subscribe(new Action1<UploadFailureEvent>() {
            @Override
            public void call(UploadFailureEvent uploadFailureEvent) {
                createOrUpdateUploadFailureNotificationFile(uploadFailureEvent.uploadStatus);
            }
        });

        DriveAdapter.BasicInfo info = drive.getBasicInfo();

        largestChangeId = info.largestChangeId;

        setRoot(info.rootFolderId);
    }

    public void start() {

        scheduler = Executors.newSingleThreadScheduledExecutor();

        scheduler.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                FileTree.this.retrieveAndApplyChanges();
            }
        }, 0, 5, TimeUnit.SECONDS);
    }

    public void tearDown() throws InterruptedException {

        if (scheduler != null) {
            scheduler.shutdown();
            scheduler.awaitTermination(5, TimeUnit.SECONDS);
            scheduler = null;
        }

        if (fileSizeUpdateEventSubscription != null)
            fileSizeUpdateEventSubscription.unsubscribe();

        if (uploadFailureEventSubscription != null)
            uploadFailureEventSubscription.unsubscribe();
    }

    public void setRoot(String id) {
        localStateLock.writeLock().lock();
        try {
            knownFiles.setRoot(fileIdStore.get(id));
        } finally {
            localStateLock.writeLock().unlock();
        }
    }

    public void reset() {
        setRoot(knownFiles.getRoot().getId().get());
    }

    public void update() {

        if (scheduler != null)
            throw new IllegalStateException(
                    "manual updates on a FileTree with scheduled retrieval of changes are not allowed");

        retrieveAndApplyChanges();
    }

    public File get(String path) throws Exception {
        return get(Paths.get(path));
    }

    public File get(Path path) throws Exception {

        File file = getOrNull(path);

        if (file == null)
            throw new NoSuchFileException(path);

        return file;
    }

    public File getOrNull(Path path) throws Exception {

        if (isRoot(path))
            return knownFiles.getRoot().toFile();

        return getOrFetch(path.getParent(), path.getFileName().toString(), singleFileGetter);
    }

    public List<String> getChildren(String path) throws Exception {
        return getChildren(Paths.get(path));
    }

    public List<String> getChildren(Path path) throws Exception {
        logger.debug("[{}] getting children", path);
        return getOrFetch(path, null, namesGetter);
    }

    public File create(String path, boolean isDirectory) throws Exception {
        return create(Paths.get(path), isDirectory);
    }

    public File create(final Path path, boolean isDirectory) throws Exception {

        logger.debug("[{}] creating {}", path, isDirectory ? "folder" : "file");

        localStateLock.writeLock().lock();

        try {

            final KnownFile parent = getUnsafe(knownFiles.getRoot(), path.getParent());
            final Path fileName = path.getFileName();

            if (getOrNullUnsafe(parent, fileName) != null)
                throw new FileAlreadyExistsException(path);

            final KnownFile newFile =
                    knownFiles.create(fileIdStore.create(), fileName.toString(), isDirectory, new Date());

            parent.tryAddChild(newFile);

            // While with other types of tasks this conversion may not be important, with create file/dir tasks it is.
            // By performing this conversion, we capture this file's current parent. Therefore, if the parent changes
            // to another directory, we will still create this file in the original directory and only then move it to
            // the new one. Not doing so can lead to a race, consider the following scenario:
            // 1. Make uplader busy with a lot of work.
            // 2. Create file F in some dir.
            // 3. Create dir D.
            // 4. Move file F to dir D.
            // Now, if uploader starts working on creation of file F before creation of dir D (which is likely),
            // the operation will fail because D's id is not known yet.
            final File file = newFile.toFile();

            String label = path + ": create " + (isDirectory ? "directory" : "file");

            uploader.submit(new Task(label, file.getId(), parent.getId(), file.isDirectory()) {
                @Override
                public void run() throws Exception {

                    syncWithRemoteLock.readLock().lock();

                    try {

                        jdbox.driveadapter.File createdFile =
                                drive.createFile(file.toDaFile(), new ByteArrayInputStream(new byte[0]));

                        localStateLock.writeLock().lock();
                        try {
                            newFile.setUploaded(createdFile.getId(), createdFile.getDownloadUrl());
                        } finally {
                            localStateLock.writeLock().unlock();
                        }

                    } finally {
                        syncWithRemoteLock.readLock().unlock();
                    }
                }
            });

            return file;

        } finally {
            localStateLock.writeLock().unlock();
        }
    }

    public void setDates(String path, Date modifiedDate, Date accessedDate) throws Exception {
        setDates(Paths.get(path), modifiedDate, accessedDate);
    }

    public void setDates(Path path, final Date modifiedDate, final Date accessedDate) throws Exception {

        logger.debug("[{}] setting dates", path);

        localStateLock.writeLock().lock();

        try {

            KnownFile existing = getUnsafe(knownFiles.getRoot(), path);

            if (existing == uploadFailureNotificationFile)
                throw new AccessDeniedException(path);

            existing.setDates(modifiedDate, accessedDate);

            final File file = existing.toFile(EnumSet.of(File.Field.MODIFIED_DATE, File.Field.ACCESSED_DATE));

            String label = path + ": set modified date to " + modifiedDate.toString() + " and accessed date to " + accessedDate;

            uploader.submit(new Task(label, file.getId()) {
                @Override
                public void run() throws Exception {
                    drive.updateFile(file.toDaFile());
                }
            });

        } finally {
            localStateLock.writeLock().unlock();
        }
    }

    public void remove(String path) throws Exception {
        remove(Paths.get(path));
    }

    public void remove(Path path) throws Exception {

        logger.debug("[{}] removing", path);

        localStateLock.writeLock().lock();

        try {

            KnownFile parent = getUnsafe(knownFiles.getRoot(), path.getParent());
            KnownFile existing = getUnsafe(parent, path.getFileName());

            if (existing.isDirectory() && getChildrenUnsafe(existing, null).size() != 0)
                throw new NonEmptyDirectoryException(path);

            parent.tryRemoveChild(existing);

            if (existing == uploadFailureNotificationFile) {

                if (openedFilesManager.getOpenedFilesCount() != 0)
                    throw new AccessDeniedException(path);

                reset();

                openedFilesManager.reset();

                uploader.reset();

                uploadFailureNotificationFile = null;

            } else {

                final File file = existing.toFile(EnumSet.of(File.Field.PARENT_IDS));

                uploader.submit(new Task(path + ": remove file/directory", file.getId()) {
                    @Override
                    public void run() throws Exception {
                        if (file.getParentIds().size() == 0)
                            drive.trashFile(file.toDaFile());
                        else
                            drive.updateFile(file.toDaFile());
                    }
                });
            }

        } finally {
            localStateLock.writeLock().unlock();
        }
    }

    public void move(String path, String newPath) throws Exception {
        move(Paths.get(path), Paths.get(newPath));
    }

    public void move(Path path, Path newPath) throws Exception {

        logger.debug("[{}] moving to {}", path, newPath);

        if (path.equals(newPath))
            return;

        localStateLock.writeLock().lock();

        try {

            Path parentPath = path.getParent();
            Path fileName = path.getFileName();
            KnownFile parent = getUnsafe(knownFiles.getRoot(), parentPath);

            KnownFile existing = getUnsafe(parent, fileName);

            if (existing == uploadFailureNotificationFile)
                throw new AccessDeniedException(path);

            Path newParentPath = newPath.getParent();
            Path newFileName = newPath.getFileName();

            KnownFile newParent;
            if (newParentPath.equals(parentPath))
                newParent = parent;
            else
                newParent = getUnsafe(knownFiles.getRoot(), newParentPath);

            if (getOrNullUnsafe(newParent, newFileName) != null)
                throw new FileAlreadyExistsException(newPath);

            EnumSet<File.Field> fields = EnumSet.noneOf(File.Field.class);

            FileId newParentId = null;

            if (!parentPath.equals(newParentPath)) {

                fields.add(File.Field.PARENT_IDS);

                newParent.tryAddChild(existing);
                parent.tryRemoveChild(existing);

                newParentId = newParent.getId();
            }

            if (!fileName.equals(newFileName)) {
                fields.add(File.Field.NAME);
                existing.rename(newFileName.toString());
            }

            final File file = existing.toFile(fields);

            uploader.submit(new Task(path + ": move/rename to " + newPath, file.getId(), newParentId) {
                @Override
                public void run() throws Exception {
                    drive.updateFile(file.toDaFile());
                }
            });

        } finally {
            localStateLock.writeLock().unlock();
        }
    }

    private <T> T getOrFetch(Path path, String fileName, Getter<T> getter) throws Exception {

        localStateLock.readLock().lock();
        try {

            KnownFile dir = locateFile(knownFiles.getRoot(), path);

            if (dir != null) {

                if (!dir.isDirectory())
                    throw new NotDirectoryException(path);

                Map<String, KnownFile> children = dir.getChildrenOrNull();
                if (children != null)
                    return getter.apply(fileName, children);
            }

        } finally {
            localStateLock.readLock().unlock();
        }

        syncWithRemoteLock.writeLock().lock();
        localStateLock.writeLock().lock();
        try {
            return getOrFetchUnsafe(knownFiles.getRoot(), path, fileName, getter);
        } finally {
            localStateLock.writeLock().unlock();
            syncWithRemoteLock.writeLock().unlock();
        }
    }

    private KnownFile locateFile(KnownFile file, Path path) {

        if (isRoot(path))
            return file;

        Map<String, KnownFile> children = file.getChildrenOrNull();

        if (children == null)
            return null;

        KnownFile child = children.get(path.getName(0).toString());

        if (child == null || path.getNameCount() == 1)
            return child;

        return locateFile(child, path.subpath(1, path.getNameCount()));
    }

    private KnownFile getUnsafe(KnownFile root, Path path) throws Exception {

        KnownFile kf = getOrNullUnsafe(root, path);

        if (kf == null)
            throw new NoSuchFileException(path);

        return kf;
    }

    private KnownFile getOrNullUnsafe(KnownFile root, Path path) throws Exception {

        if (isRoot(path))
            return root;

        return getOrFetchUnsafe(root, path.getParent(), path.getFileName().toString(), singleKnownFileGetter);
    }

    private List<String> getChildrenUnsafe(KnownFile root, Path path) throws Exception {
        return getOrFetchUnsafe(root, path, null, namesGetter);
    }

    private <T> T getOrFetchUnsafe(KnownFile root, Path path, String fileName, Getter<T> getter) throws Exception {

        KnownFile dir = getUnsafe(root, path);

        if (!dir.isDirectory())
            throw new NotDirectoryException(path);

        Map<String, KnownFile> children = dir.getChildrenOrNull();
        if (children != null)
            return getter.apply(fileName, children);

        dir.setTracked();

        if (dir.getId().isSet()) {
            for (jdbox.driveadapter.File child : drive.getChildren(dir.toFile().toDaFile())) {
                File file = new File(fileIdStore, child);
                KnownFile existing = knownFiles.get(file.getId());
                if (existing != null)
                    dir.tryAddChild(existing);
                else
                    dir.tryAddChild(knownFiles.create(file));
            }
        }

        return getter.apply(fileName, dir.getChildrenOrNull());
    }

    private void updateFileSize(FileId fileId, long fileSize) {
        localStateLock.writeLock().lock();
        try {
            KnownFile file = knownFiles.get(fileId);
            if (file != null)
                file.setSize(fileSize);
        } finally {
            localStateLock.writeLock().unlock();
        }
    }

    private void createOrUpdateUploadFailureNotificationFile(Uploader.UploadStatus uploadStatus) {

        localStateLock.writeLock().lock();
        try {

            if (uploadFailureNotificationFile != null) {

                uploadFailureNotificationFile.setDates(uploadStatus.date, uploadStatus.date);

            } else {

                uploadFailureNotificationFile = knownFiles.create(
                        fileIdStore.get(Uploader.uploadFailureNotificationFileId),
                        uploadNotificationFileName, false, uploadStatus.date);

                uploadFailureNotificationFile.setDates(uploadStatus.date, uploadStatus.date);

                knownFiles.getRoot().tryAddChild(uploadFailureNotificationFile);
            }

        } finally {
            localStateLock.writeLock().unlock();
        }
    }

    private void retrieveAndApplyChanges() {

        boolean locked = false;

        try {
            locked = syncWithRemoteLock.writeLock().tryLock(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            logger.debug("lock acquisition has been interrupted", e);
        }

        if (!locked)
            return;

        try {

            DriveAdapter.Changes changes = drive.getChanges(largestChangeId + 1);

            largestChangeId = changes.largestChangeId;

            for (DriveAdapter.Change change : changes.items)
                tryApplyChange(change);

        } catch (Exception e) {
            logger.error("an error occured retrieving a list of changes", e);
        } finally {
            syncWithRemoteLock.writeLock().unlock();
        }
    }

    private void tryApplyChange(DriveAdapter.Change change) throws Exception {

        localStateLock.writeLock().lock();

        try {

            FileId changedFileId = fileIdStore.get(change.fileId);

            if (uploader.fileIsQueued(changedFileId))
                return;

            File changedFile = change.file != null ? new File(fileIdStore, change.file) : null;

            KnownFile currentFile = knownFiles.get(changedFileId);

            if (currentFile == null && changedFile != null && !changedFile.isTrashed()) {

                logger.debug("adding {} to tree", changedFile);

                KnownFile file = knownFiles.create(changedFile);

                for (FileId parentId : changedFile.getParentIds()) {
                    KnownFile parent = knownFiles.get(parentId);
                    if (parent != null)
                        parent.tryAddChild(file);
                }

            } else if (currentFile != null && knownFiles.getRoot() != currentFile) {

                if (changedFile == null || changedFile.isTrashed()) {

                    logger.debug("removing {} from tree", currentFile);

                    for (KnownFile parent : currentFile.getParents())
                        parent.tryRemoveChild(currentFile);

                } else {

                    logger.debug("updating existing file with {}", changedFile);

                    currentFile.rename(changedFile.getName());
                    currentFile.update(changedFile);

                    logger.debug("ensuring that {} is correctly placed in tree", changedFile);

                    for (FileId parentId : changedFile.getParentIds()) {
                        KnownFile parent = knownFiles.get(parentId);
                        if (parent != null)
                            parent.tryAddChild(currentFile);
                    }

                    for (KnownFile parent : new HashSet<>(currentFile.getParents())) {
                        if (!changedFile.getParentIds().contains(parent.getId()))
                            parent.tryRemoveChild(currentFile);
                    }
                }
            }
        } finally {
            localStateLock.writeLock().unlock();
        }
    }

    private boolean isRoot(Path path) {
        return path == null || path.equals(Paths.get("/"));
    }

    public class NoSuchFileException extends Exception {
        public NoSuchFileException(Path path) {
            super(path.toString());
        }
    }

    public class NotDirectoryException extends Exception {
        public NotDirectoryException(Path path) {
            super(path.toString());
        }
    }

    public class FileAlreadyExistsException extends Exception {
        public FileAlreadyExistsException(Path path) {
            super(path.toString());
        }
    }

    public class NonEmptyDirectoryException extends Exception {
        public NonEmptyDirectoryException(Path path) {
            super(path.toString());
        }
    }

    public class AccessDeniedException extends Exception {
        public AccessDeniedException(Path path) {
            super(path.toString());
        }
    }

    private interface Getter<T> {
        T apply(String fileName, Map<String, KnownFile> files);
    }
}
