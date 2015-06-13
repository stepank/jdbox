package jdbox.filetree;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.SettableFuture;
import com.google.inject.Inject;
import jdbox.driveadapter.DriveAdapter;
import jdbox.filetree.knownfiles.KnownFile;
import jdbox.filetree.knownfiles.KnownFiles;
import jdbox.models.File;
import jdbox.models.fileids.FileId;
import jdbox.models.fileids.FileIdStore;
import jdbox.uploader.Task;
import jdbox.uploader.Uploader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class FileTree {

    private static ImmutableMap<String, String> extensions = ImmutableMap.copyOf(new HashMap<String, String>() {{
        put("application/pdf", "pdf");
        put("image/png", "png");
        put("image/git", "gif");
        put("image/jpeg", "jpg");
        put("application/vnd.google-apps.drawing", "desktop");
        put("application/vnd.google-apps.document", "desktop");
        put("application/vnd.google-apps.presentation", "desktop");
        put("application/vnd.google-apps.spreadsheet", "desktop");
    }});

    private static final Logger logger = LoggerFactory.getLogger(FileTree.class);
    private static final Path rootPath = Paths.get("/");

    private final DriveAdapter drive;
    private final ScheduledExecutorService scheduler;
    private final KnownFiles knownFiles = new KnownFiles();
    private final SettableFuture syncError = SettableFuture.create();
    private final CountDownLatch start = new CountDownLatch(1);
    private final ReadWriteLock readWriteLock = new ReentrantReadWriteLock();
    private final FileIdStore fileIdStore;
    private final Uploader uploader;
    private final boolean autoUpdate;

    private volatile long largestChangeId;

    private final static Getter<List<String>> namesGetter = new Getter<List<String>>() {
        @Override
        public List<String> apply(String fileName, final Map<String, KnownFile> files) {
            return ImmutableList.copyOf(
                    Collections2.transform(files.entrySet(), new Function<Map.Entry<String, KnownFile>, String>() {
                        @Nullable
                        @Override
                        public String apply(Map.Entry<String, KnownFile> entry) {

                            String fileName = entry.getValue().getName();
                            KnownFile file = entry.getValue();

                            String extension = extensions.get(file.getMimeType());

                            if (extension == null || fileName.endsWith(extension))
                                return fileName;

                            String newFileName = fileName + "." + extension;
                            return files.containsKey(newFileName) ? fileName : newFileName;
                        }
                    }));
        }
    };

    private final static Getter<File> singleFileGetter = new Getter<File>() {
        @Override
        public File apply(String fileName, Map<String, KnownFile> files) {
            KnownFile kf = singleKnownFileGetter.apply(fileName, files);
            File file = kf != null ? kf.toFile() : null;
            logger.debug(fileName + " " + (file != null ? file.getSize() : ""));
            return file;
        }
    };

    private final static Getter<KnownFile> singleKnownFileGetter = new Getter<KnownFile>() {
        @Override
        public KnownFile apply(String fileName, Map<String, KnownFile> files) {

            KnownFile kf = files.get(fileName);
            if (kf != null)
                return kf;

            int i = fileName.lastIndexOf('.');
            if (i < 0)
                return null;

            kf = files.get(fileName.substring(0, i));
            if (kf == null)
                return null;

            return fileName.substring(i + 1).equals(extensions.get(kf.getMimeType())) ? kf : null;
        }
    };

    @Inject
    public FileTree(DriveAdapter drive, FileIdStore fileIdStore, Uploader uploader, boolean autoUpdate) {

        this.drive = drive;
        this.fileIdStore = fileIdStore;
        this.uploader = uploader;
        this.autoUpdate = autoUpdate;

        this.scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    public int getKnownFileCount() {
        readWriteLock.readLock().lock();
        try {
            return knownFiles.getFileCount();
        } finally {
            readWriteLock.readLock().unlock();
        }
    }

    public int getTrackedDirCount() {
        readWriteLock.readLock().lock();
        try {
            return knownFiles.getTrackedDirCount();
        } finally {
            readWriteLock.readLock().unlock();
        }
    }

    public void start() throws Exception {

        if (start.getCount() == 0)
            return;

        DriveAdapter.BasicInfo info = drive.getBasicInfo();

        largestChangeId = info.largestChangeId;

        readWriteLock.writeLock().lock();
        try {
            knownFiles.setRoot(fileIdStore.get(info.rootFolderId));
        } finally {
            readWriteLock.writeLock().unlock();
        }

        start.countDown();

        if (autoUpdate)
            scheduler.scheduleAtFixedRate(new Runnable() {
                @Override
                public void run() {
                    FileTree.this.retrieveAndApplyChanges();
                }
            }, 0, 5, TimeUnit.SECONDS);
    }

    public void stopAndWait(int timeout) throws InterruptedException {

        if (!autoUpdate)
            return;

        scheduler.shutdown();
        scheduler.awaitTermination(timeout, TimeUnit.SECONDS);
    }

    public void setRoot(String id) throws InterruptedException {

        start.await();

        readWriteLock.writeLock().lock();
        try {
            knownFiles.setRoot(fileIdStore.get(id));
        } finally {
            readWriteLock.writeLock().unlock();
        }
    }

    public void reset() throws InterruptedException {
        setRoot(knownFiles.getRoot().getId().get());
    }

    public void update() {

        if (autoUpdate)
            return;

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

        readWriteLock.writeLock().lock();

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

            uploader.submit(new Task("create file/dir", file.getId(), parent.getId(), true) {
                @Override
                public void run() throws Exception {

                    readWriteLock.writeLock().lock();
                    try {

                        jdbox.driveadapter.File createdFile =
                                drive.createFile(file.toDaFile(), new ByteArrayInputStream(new byte[0]));

                        newFile.setUploaded(createdFile.getId(), createdFile.getDownloadUrl());

                    } finally {
                        readWriteLock.writeLock().unlock();
                    }
                }
            });

            return file;

        } finally {
            readWriteLock.writeLock().unlock();
        }
    }

    public void setDates(String path, Date modifiedDate, Date accessedDate) throws Exception {
        setDates(Paths.get(path), modifiedDate, accessedDate);
    }

    public void setDates(Path path, final Date modifiedDate, final Date accessedDate) throws Exception {

        logger.debug("[{}] setting dates", path);

        readWriteLock.writeLock().lock();

        try {

            KnownFile existing = getUnsafe(knownFiles.getRoot(), path);
            existing.setDates(modifiedDate, accessedDate);

            final File file = existing.toFile(EnumSet.of(File.Field.MODIFIED_DATE, File.Field.ACCESSED_DATE));

            uploader.submit(new Task("set dates", file.getId()) {
                @Override
                public void run() throws Exception {
                    drive.updateFile(file.toDaFile());
                }
            });

        } finally {
            readWriteLock.writeLock().unlock();
        }
    }

    public void remove(String path) throws Exception {
        remove(Paths.get(path));
    }

    public void remove(Path path) throws Exception {

        logger.debug("[{}] removing", path);

        readWriteLock.writeLock().lock();

        try {

            KnownFile parent = getUnsafe(knownFiles.getRoot(), path.getParent());
            KnownFile existing = getUnsafe(parent, path.getFileName());

            if (existing.isDirectory() && getChildrenUnsafe(existing, null).size() != 0)
                throw new NonEmptyDirectoryException(path);

            parent.tryRemoveChild(existing);

            final File file = existing.toFile(EnumSet.of(File.Field.PARENT_IDS));

            uploader.submit(new Task("remove file/dir", file.getId()) {
                @Override
                public void run() throws Exception {
                    if (file.getParentIds().size() == 0)
                        drive.trashFile(file.toDaFile());
                    else
                        drive.updateFile(file.toDaFile());
                }
            });

        } finally {
            readWriteLock.writeLock().unlock();
        }
    }

    public void move(String path, String newPath) throws Exception {
        move(Paths.get(path), Paths.get(newPath));
    }

    public void move(Path path, Path newPath) throws Exception {

        logger.debug("[{}] moving to {}", path, newPath);

        if (path.equals(newPath))
            return;

        readWriteLock.writeLock().lock();

        try {

            Path parentPath = path.getParent();
            Path fileName = path.getFileName();
            KnownFile parent = getUnsafe(knownFiles.getRoot(), parentPath);

            KnownFile existing = getUnsafe(parent, fileName);

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

            uploader.submit(new Task("set dates", file.getId(), newParentId) {
                @Override
                public void run() throws Exception {
                    drive.updateFile(file.toDaFile());
                }
            });

        } finally {
            readWriteLock.writeLock().unlock();
        }
    }

    public void updateFileSize(File file) {
        readWriteLock.writeLock().lock();
        try {
            knownFiles.get(file.getId()).setSize(file.getSize());
        } finally {
            readWriteLock.writeLock().unlock();
        }
    }

    private <T> T getOrFetch(Path path, String fileName, Getter<T> getter) throws Exception {

        readWriteLock.readLock().lock();
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
            readWriteLock.readLock().unlock();
        }

        readWriteLock.writeLock().lock();
        try {
            return getOrFetchUnsafe(knownFiles.getRoot(), path, fileName, getter);
        } finally {
            readWriteLock.writeLock().unlock();
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

        start.await();

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

    private void retrieveAndApplyChanges() {

        try {

            DriveAdapter.Changes changes = drive.getChanges(largestChangeId + 1);

            largestChangeId = changes.largestChangeId;

            for (DriveAdapter.Change change : changes.items) {
                FileTree.this.tryApplyChange(change);
            }
        } catch (Exception e) {
            logger.error("an error occured retrieving a list of changes", e);
            syncError.setException(e);
        }
    }

    private void tryApplyChange(DriveAdapter.Change change) throws Exception {

        readWriteLock.writeLock().lock();

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

            } else if (currentFile != null) {

                if (changedFile == null || changedFile.isTrashed()) {

                    logger.debug("removing {} from tree", currentFile);

                    for (KnownFile parent : currentFile.getParents())
                        parent.tryRemoveChild(currentFile);

                } else {

                    logger.debug("updating existing file with {}", change);

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
            readWriteLock.writeLock().unlock();
        }
    }

    private boolean isRoot(Path path) {
        return path == null || path.equals(rootPath);
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

    private interface Getter<T> {
        T apply(String fileName, Map<String, KnownFile> files);
    }
}
