package jdbox.filetree;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import jdbox.content.OpenedFilesManager;
import jdbox.driveadapter.Change;
import jdbox.driveadapter.Changes;
import jdbox.driveadapter.DriveAdapter;
import jdbox.driveadapter.Field;
import jdbox.localstate.LocalState;
import jdbox.localstate.interfaces.*;
import jdbox.localstate.knownfiles.KnownFile;
import jdbox.localstate.knownfiles.KnownFiles;
import jdbox.models.File;
import jdbox.models.fileids.FileId;
import jdbox.models.fileids.FileIdStore;
import jdbox.datapersist.ChangeSet;
import jdbox.uploader.DriveTask;
import jdbox.uploader.FileEtagUpdateEvent;
import jdbox.uploader.UploadFailureEvent;
import jdbox.uploader.Uploader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Subscription;
import rx.functions.Action1;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class FileTree {

    private static final Logger logger = LoggerFactory.getLogger(FileTree.class);

    private final DriveAdapter drive;
    private final FileIdStore fileIdStore;
    private final Observable<FileEtagUpdateEvent> fileEtagUpdateEvent;
    private final rx.Observable<UploadFailureEvent> uploadFailureEvent;
    private final OpenedFilesManager openedFilesManager;

    private final LocalState localState;

    private volatile ScheduledExecutorService scheduler;
    private volatile Subscription fileEtagUpdateEventSubscription;
    private volatile Subscription uploadFailureEventSubscription;

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
            Observable<FileEtagUpdateEvent> fileEtagUpdateEvent, Observable<UploadFailureEvent> uploadFailureEvent,
            OpenedFilesManager openedFilesManager, LocalState localState) {
        this.drive = drive;
        this.fileIdStore = fileIdStore;
        this.fileEtagUpdateEvent = fileEtagUpdateEvent;
        this.uploadFailureEvent = uploadFailureEvent;
        this.openedFilesManager = openedFilesManager;
        this.localState = localState;
    }

    public int getKnownFileCount() {
        return localState.read(new LocalReadSafe<Integer>() {
            @Override
            public Integer run(KnownFiles knownFiles) {
                return knownFiles.getFileCount();
            }
        });
    }

    public int getTrackedDirCount() {
        return localState.read(new LocalReadSafe<Integer>() {
            @Override
            public Integer run(KnownFiles knownFiles) {
                return knownFiles.getTrackedDirCount();
            }
        });
    }

    public void init() throws IOException {

        fileEtagUpdateEventSubscription = fileEtagUpdateEvent.subscribe(new Action1<FileEtagUpdateEvent>() {
            @Override
            public void call(FileEtagUpdateEvent event) {
                updateFileEtag(event.fileId, event.etag);
            }
        });

        uploadFailureEventSubscription = uploadFailureEvent.subscribe(new Action1<UploadFailureEvent>() {
            @Override
            public void call(UploadFailureEvent uploadFailureEvent) {
                createOrUpdateUploadFailureNotificationFile(uploadFailureEvent.uploadStatus);
            }
        });
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

        if (fileEtagUpdateEventSubscription != null)
            fileEtagUpdateEventSubscription.unsubscribe();

        if (uploadFailureEventSubscription != null)
            uploadFailureEventSubscription.unsubscribe();
    }

    public void update() {

        if (scheduler != null)
            throw new IllegalStateException(
                    "manual updates on a FileTree with scheduled retrieval of changes are not allowed");

        retrieveAndApplyChanges();
    }

    public File get(String path) throws IOException {
        return get(Paths.get(path));
    }

    public File get(Path path) throws IOException {

        File file = getOrNull(path);

        if (file == null)
            throw new NoSuchFileException(path);

        return file;
    }

    public File getOrNull(Path path) throws IOException {

        if (isRoot(path)) {
            return localState.read(new LocalReadSafe<File>() {
                @Override
                public File run(KnownFiles knownFiles) {
                    return knownFiles.getRoot().toFile();
                }
            });
        }

        return getOrFetch(path.getParent(), path.getFileName().toString(), singleFileGetter);
    }

    public List<String> getChildren(String path) throws IOException {
        return getChildren(Paths.get(path));
    }

    public List<String> getChildren(Path path) throws IOException {
        logger.debug("getting children");
        return getOrFetch(path, null, namesGetter);
    }

    public File create(String path, boolean isDirectory) throws IOException {
        return create(Paths.get(path), isDirectory);
    }

    public File create(final Path path, final boolean isDirectory) throws IOException {

        logger.debug("creating {}", isDirectory ? "folder" : "file");

        return localState.update(
                new FilePropertiesLocalUpdate(path) {
                    @Override
                    public KnownFile run(
                            KnownFile existing, KnownFile parent, KnownFiles knownFiles, Uploader uploader)
                            throws IOException {

                        if (!parent.isDirectory())
                            throw new NotDirectoryException(path.getParent());

                        if (existing != null)
                            throw new FileAlreadyExistsException(path);

                        Date now = new Date();
                        final KnownFile newFile = knownFiles.create(
                                fileIdStore.create(), path.getFileName().toString(), isDirectory, now);
                        newFile.setDates(now, now);
                        newFile.setContentProperties(0, "d41d8cd98f00b204e9800998ecf8427e"); // empty file md5

                        parent.tryAddChild(newFile);

                        uploader.submit(new DriveTask(
                                fileIdStore, drive,
                                "create " + (isDirectory ? "directory" : "file"), null, newFile.toFile(),
                                EnumSet.allOf(Field.class), parent.getId(), true) {
                            @Override
                            public jdbox.driveadapter.File run(
                                    ChangeSet changeSet, jdbox.driveadapter.File file) throws IOException {

                                final jdbox.driveadapter.File createdFile = drive.createFile(
                                        file, new ByteArrayInputStream(new byte[0]));

                                localState.update(new LocalUpdateSafe() {
                                    @Override
                                    public void run(
                                            KnownFiles knownFiles, Uploader uploader) {
                                        newFile.setUploaded(
                                                createdFile.getId(), createdFile.getMimeType(),
                                                createdFile.getDownloadUrl(), createdFile.getAlternateLink());
                                    }
                                });

                                return createdFile;
                            }
                        });

                        return newFile;
                    }
                }
        );
    }

    public void setDates(String path, Date modifiedDate, Date accessedDate) throws IOException {
        setDates(Paths.get(path), modifiedDate, accessedDate);
    }

    public void setDates(final Path path, final Date modifiedDate, final Date accessedDate) throws IOException {

        logger.debug("setting dates");

        localState.update(new FilePropertiesLocalUpdate(path) {
            @Override
            public KnownFile run(
                    KnownFile existing, KnownFile parent, KnownFiles knownFiles, Uploader uploader)
                    throws IOException {

                if (existing == knownFiles.getUploadFailureNotificationFile())
                    throw new AccessDeniedException(path);

                File original = existing.toFile();

                existing.setDates(modifiedDate, accessedDate);

                uploader.submit(new DriveTask(
                        fileIdStore, drive,
                        "set modified to " + modifiedDate.toString() + " and accessed to " + accessedDate,
                        original, existing.toFile(), EnumSet.of(Field.MODIFIED_DATE, Field.ACCESSED_DATE)) {
                    @Override
                    public jdbox.driveadapter.File run(
                            ChangeSet changeSet, jdbox.driveadapter.File file) throws IOException {
                        return drive.updateFile(file);
                    }
                });

                return null;
            }
        });
    }

    public void remove(String path) throws IOException {
        remove(Paths.get(path));
    }

    public void remove(final Path path) throws IOException {

        logger.debug("removing");

        localState.update(new FilePropertiesLocalUpdate(path) {
            @Override
            public KnownFile run(
                    KnownFile existing, KnownFile parent, KnownFiles knownFiles, Uploader uploader)
                    throws IOException {

                if (existing.isDirectory() && getChildrenUnsafe(knownFiles, existing, null).size() != 0)
                    throw new NonEmptyDirectoryException(path);

                File original = existing.toFile();

                parent.tryRemoveChild(existing);

                if (existing == knownFiles.getUploadFailureNotificationFile()) {

                    if (openedFilesManager.getOpenedFilesCount() != 0)
                        throw new AccessDeniedException(path);

                    knownFiles.reset();

                    openedFilesManager.reset();

                    uploader.reset();

                } else {

                    File file = existing.toFile();

                    if (file.getParentIds().size() == 0) {

                        uploader.submit(new DriveTask(
                                fileIdStore, drive, "remove file/directory completely",
                                original, file, EnumSet.noneOf(Field.class)) {
                            @Override
                            public jdbox.driveadapter.File run(
                                    ChangeSet changeSet, jdbox.driveadapter.File file) throws IOException {
                                return drive.trashFile(file);
                            }
                        });

                    } else {

                        uploader.submit(new DriveTask(
                                fileIdStore, drive, "remove file/directory from one directory only",
                                original, file, EnumSet.of(Field.PARENT_IDS)) {
                            @Override
                            public jdbox.driveadapter.File run(
                                    ChangeSet changeSet, jdbox.driveadapter.File file) throws IOException {
                                return drive.updateFile(file);
                            }
                        });
                    }
                }

                return null;
            }
        });
    }

    public void move(String path, String newPath) throws IOException {
        move(Paths.get(path), Paths.get(newPath));
    }

    public void move(final Path path, final Path newPath) throws IOException {

        logger.debug("moving to {}", newPath);

        if (path.equals(newPath))
            return;

        localState.update(new FilePropertiesLocalUpdate(path) {
            @Override
            public KnownFile run(
                    KnownFile existing, KnownFile parent, KnownFiles knownFiles, Uploader uploader)
                    throws IOException {

                Path parentPath = path.getParent();
                Path fileName = path.getFileName();

                if (existing == knownFiles.getUploadFailureNotificationFile())
                    throw new AccessDeniedException(path);

                File original = existing.toFile();

                Path newParentPath = newPath.getParent();
                Path newFileName = newPath.getFileName();

                KnownFile newParent;
                if (newParentPath.equals(parentPath))
                    newParent = parent;
                else
                    newParent = getUnsafe(knownFiles, knownFiles.getRoot(), newParentPath);

                if (getOrNullUnsafe(knownFiles, newParent, newFileName) != null)
                    throw new FileAlreadyExistsException(newPath);

                EnumSet<Field> fields = EnumSet.noneOf(Field.class);

                FileId newParentId = null;

                if (!parentPath.equals(newParentPath)) {

                    fields.add(Field.PARENT_IDS);

                    newParent.tryAddChild(existing);
                    parent.tryRemoveChild(existing);

                    newParentId = newParent.getId();
                }

                if (!fileName.equals(newFileName)) {
                    fields.add(Field.NAME);
                    existing.rename(newFileName.toString());
                }

                uploader.submit(new DriveTask(
                        fileIdStore, drive, "move/rename to " + newPath,
                        original, existing.toFile(), fields, newParentId) {
                    @Override
                    public jdbox.driveadapter.File run(
                            ChangeSet changeSet, jdbox.driveadapter.File file) throws IOException {
                        return drive.updateFile(file);
                    }
                });

                return null;
            }
        });
    }

    private <T> T getOrFetch(final Path path, final String fileName, final Getter<T> getter) throws IOException {

        T result = localState.read(new LocalRead<T>() {
            @Override
            public T run(KnownFiles knownFiles) throws NotDirectoryException {

                KnownFile dir = locateFile(knownFiles.getRoot(), path);

                if (dir == null)
                    return null;

                if (!dir.isDirectory())
                    throw new NotDirectoryException(path);

                Map<String, KnownFile> children = dir.getChildrenOrNull();
                return children != null ? getter.apply(fileName, children) : null;
            }
        });

        if (result != null)
            return result;

        return localState.update(new RemoteRead<T>() {
            @Override
            public T run() throws IOException {
                return localState.update(new LocalUpdate<T>() {
                    @Override
                    public T run(KnownFiles knownFiles, Uploader uploader) throws IOException {
                        return getOrFetchUnsafe(knownFiles, knownFiles.getRoot(), path, fileName, getter);
                    }
                });
            }
        });
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

    private KnownFile getUnsafe(KnownFiles knownFiles, KnownFile root, Path path) throws IOException {

        KnownFile kf = getOrNullUnsafe(knownFiles, root, path);

        if (kf == null)
            throw new NoSuchFileException(path);

        return kf;
    }

    private KnownFile getOrNullUnsafe(KnownFiles knownFiles, KnownFile root, Path path) throws IOException {

        if (isRoot(path))
            return root;

        return getOrFetchUnsafe(
                knownFiles, root, path.getParent(), path.getFileName().toString(), singleKnownFileGetter);
    }

    private List<String> getChildrenUnsafe(KnownFiles knownFiles, KnownFile root, Path path) throws IOException {
        return getOrFetchUnsafe(knownFiles, root, path, null, namesGetter);
    }

    private <T> T getOrFetchUnsafe(
            KnownFiles knownFiles, KnownFile root, Path path, String fileName, Getter<T> getter) throws IOException {

        KnownFile dir = getUnsafe(knownFiles, root, path);

        if (!dir.isDirectory())
            throw new NotDirectoryException(path);

        Map<String, KnownFile> children = dir.getChildrenOrNull();
        if (children != null)
            return getter.apply(fileName, children);

        if (!dir.getId().isSet()) {

            dir.setTracked();

        } else {

            List<jdbox.driveadapter.File> retrieved = drive.getChildren(dir.toFile().toDaFile());

            dir.setTracked();

            for (jdbox.driveadapter.File child : retrieved) {
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

    private void updateFileEtag(final FileId fileId, final String etag) {
        localState.update(new LocalUpdateSafe() {
            @Override
            public void run(KnownFiles knownFiles, Uploader uploader) {
                KnownFile file = knownFiles.get(fileId);
                if (file != null)
                    file.setEtag(etag);
            }
        });
    }

    private void createOrUpdateUploadFailureNotificationFile(final Uploader.UploadStatus uploadStatus) {
        localState.update(new LocalUpdateSafe() {
            @Override
            public void run(KnownFiles knownFiles, Uploader uploader) {
                knownFiles.createOrUpdateUploadFailureNotificationFile(uploadStatus.date);
            }
        });
    }

    private void retrieveAndApplyChanges() {

        try {

            localState.tryUpdate(new RemoteReadVoid() {
                @Override
                public void run() throws IOException {

                    final Changes changes = drive.getChanges(localState.getLargestChangeId() + 1);

                    logger.debug("got {} changes, largest is {}", changes.items.size(), changes.largestChangeId);

                    if (changes.items.size() == 0)
                        return;

                    localState.update(new LocalUpdateSafe() {
                        @Override
                        public void run(KnownFiles knownFiles, Uploader uploader) {

                            knownFiles.setLargestChangeId(changes.largestChangeId);

                            for (Change change : changes.items)
                                tryApplyChange(knownFiles, uploader, change);
                        }
                    });
                }
            }, 5, TimeUnit.SECONDS);

        } catch (IOException e) {
            logger.error("an error occured retrieving a list of changes", e);
        }
    }

    private void tryApplyChange(KnownFiles knownFiles, Uploader uploader, Change change) {

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
    }

    private boolean isRoot(Path path) {
        return path == null || path.equals(Paths.get("/"));
    }

    public class NoSuchFileException extends IOException {
        public NoSuchFileException(Path path) {
            super(path.toString());
        }
    }

    public class NotDirectoryException extends IOException {
        public NotDirectoryException(Path path) {
            super(path.toString());
        }
    }

    public class FileAlreadyExistsException extends IOException {
        public FileAlreadyExistsException(Path path) {
            super(path.toString());
        }
    }

    public class NonEmptyDirectoryException extends IOException {
        public NonEmptyDirectoryException(Path path) {
            super(path.toString());
        }
    }

    public class AccessDeniedException extends IOException {
        public AccessDeniedException(Path path) {
            super(path.toString());
        }
    }

    private interface Getter<T> {
        T apply(String fileName, Map<String, KnownFile> files);
    }

    private abstract class FilePropertiesLocalUpdate implements LocalUpdate<File> {

        private final Path path;

        public FilePropertiesLocalUpdate(Path path) {
            this.path = path;
        }

        @Override
        public File run(KnownFiles knownFiles, Uploader uploader) throws IOException {

            Path parentPath = path.getParent();
            Path fileName = path.getFileName();
            KnownFile parent = getUnsafe(knownFiles, knownFiles.getRoot(), parentPath);

            KnownFile existing = getOrNullUnsafe(knownFiles, parent, fileName);

            KnownFile updated = run(existing, parent, knownFiles, uploader);

            return updated != null ? updated.toFile() : null;
        }

        public abstract KnownFile run(
                KnownFile existing, KnownFile parent, KnownFiles knownFiles, Uploader uploader) throws IOException;
    }
}
