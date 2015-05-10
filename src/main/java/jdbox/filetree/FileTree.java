package jdbox.filetree;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.SettableFuture;
import com.google.inject.Inject;
import jdbox.DriveAdapter;
import jdbox.Uploader;
import jdbox.filetree.knownfiles.KnownFile;
import jdbox.filetree.knownfiles.KnownFiles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
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
    private final ScheduledExecutorService executor;
    private final KnownFiles knownFiles = new KnownFiles();
    private final SettableFuture syncError = SettableFuture.create();
    private final CountDownLatch start = new CountDownLatch(1);
    private final ReadWriteLock readWriteLock = new ReentrantReadWriteLock();
    private final Semaphore applyChangesSemaphore = new Semaphore(1);
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

                            String fileName = entry.getKey();
                            File file = entry.getValue().self;

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
            return kf != null ? kf.self : null;
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

            return fileName.substring(i + 1).equals(extensions.get(kf.self.getMimeType())) ? kf : null;
        }
    };

    @Inject
    public FileTree(
            DriveAdapter drive, Uploader uploader, ScheduledExecutorService executor, boolean autoUpdate) {
        this.drive = drive;
        this.uploader = uploader;
        this.executor = executor;
        this.autoUpdate = autoUpdate;
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
            knownFiles.setRoot(new KnownFile(File.getRoot(info.rootFolderId)));
        } finally {
            readWriteLock.writeLock().unlock();
        }

        start.countDown();

        if (autoUpdate)
            executor.scheduleAtFixedRate(new Runnable() {
                @Override
                public void run() {
                    FileTree.this.retrieveAndApplyChanges();
                }
            }, 0, 5, TimeUnit.SECONDS);
    }

    public void setRoot(File file) throws InterruptedException {

        start.await();

        readWriteLock.writeLock().lock();
        try {
            knownFiles.setRoot(new KnownFile(file));
        } finally {
            readWriteLock.writeLock().unlock();
        }
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
            return knownFiles.getRoot().self;

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

            final KnownFile file =
                    new KnownFile(new File(fileName.toString(), parent.self, isDirectory));
            file.self.setCreatedDate(new Date());

            knownFiles.tryAddChild(parent, file);

            uploader.submit(new Runnable() {
                @Override
                public void run() {
                    applyChangesSemaphore.acquireUninterruptibly();
                    try {
                        File createdFile = drive.createFile(file.self, new ByteArrayInputStream(new byte[0]));
                        logger.debug("created {}", file);
                        readWriteLock.writeLock().lock();
                        try {
                            file.self.setUploaded(createdFile);
                            knownFiles.put(file);
                        } finally {
                            readWriteLock.writeLock().unlock();
                        }
                    } catch (Exception e) {
                        logger.error("an error occured while creating file", e);
                    } finally {
                        applyChangesSemaphore.release();
                    }
                }
            });

            return file.self;

        } finally {
            readWriteLock.writeLock().unlock();
        }
    }

    public void setDates(String path, Date accessedDate, Date modifiedDate) throws Exception {
        setDates(Paths.get(path), accessedDate, modifiedDate);
    }

    public void setDates(Path path, final Date accessedDate, final Date modifiedDate) throws Exception {

        logger.debug("[{}] setting dates", path);

        final File file = get(path);

        file.setAccessedDate(accessedDate);
        file.setModifiedDate(modifiedDate);

        uploader.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    drive.touchFile(file, true);
                    logger.debug("set dates for {}", file);
                } catch (Exception e) {
                    logger.error("an error occured while setting dates", e);
                }
            }
        });
    }

    public void remove(String path) throws Exception {
        remove(Paths.get(path));
    }

    public void remove(Path path) throws Exception {

        logger.debug("[{}] removing", path);

        readWriteLock.writeLock().lock();

        try {

            final KnownFile parent = getUnsafe(knownFiles.getRoot(), path.getParent());
            final KnownFile file = getUnsafe(parent, path.getFileName());

            if (file.self.isDirectory() && getChildrenUnsafe(file, null).size() != 0)
                throw new NonEmptyDirectoryException(path);

            knownFiles.remove(file);

            uploader.submit(new Runnable() {
                @Override
                public void run() {
                    applyChangesSemaphore.acquireUninterruptibly();
                    try {
                        Collection<FileId> parentIds = file.self.getParentIds();
                        if (parentIds.size() == 1)
                            drive.trashFile(file.self);
                        else {
                            parentIds.remove(parent.self.getId());
                            drive.updateParentIds(file.self);
                        }
                        logger.debug("removed {}", file);
                    } catch (Exception e) {
                        logger.error("an error occured while removing file", e);
                    } finally {
                        applyChangesSemaphore.release();
                    }
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

            final Path parentPath = path.getParent();
            final Path fileName = path.getFileName();
            final KnownFile parent = getUnsafe(knownFiles.getRoot(), parentPath);

            final KnownFile file = getUnsafe(parent, fileName);

            final Path newParentPath = newPath.getParent();
            final Path newFileName = newPath.getFileName();

            final KnownFile newParent;
            if (newParentPath.equals(parentPath))
                newParent = parent;
            else
                newParent = getUnsafe(knownFiles.getRoot(), newParentPath);

            if (getOrNullUnsafe(newParent, newFileName) != null)
                throw new FileAlreadyExistsException(newPath);

            knownFiles.tryRemoveChild(parent, file);

            if (!fileName.equals(newFileName)) {
                file.self.setName(newFileName.toString());
            }

            knownFiles.tryAddChild(newParent, file);

            if (!parentPath.equals(newParentPath)) {
                file.self.getParentIds().remove(parent.self.getId());
                file.self.getParentIds().add(newParent.self.getId());
            }

            uploader.submit(new Runnable() {
                @Override
                public void run() {

                    applyChangesSemaphore.acquireUninterruptibly();

                    try {

                        EnumSet<DriveAdapter.Field> fields = EnumSet.noneOf(DriveAdapter.Field.class);

                        if (!parentPath.equals(newParentPath))
                            fields.add(DriveAdapter.Field.PARENT_IDS);

                        if (!fileName.equals(newFileName))
                            fields.add(DriveAdapter.Field.NAME);

                        drive.updateFile(file.self, fields);
                        logger.debug("moved {}", file);

                    } catch (Exception e) {
                        logger.error("an error occured while moving file", e);
                    } finally {
                        applyChangesSemaphore.release();
                    }
                }
            });

        } finally {
            readWriteLock.writeLock().unlock();
        }
    }

    private <T> T getOrFetch(Path path, String fileName, Getter<T> getter) throws Exception {

        readWriteLock.readLock().lock();
        try {

            KnownFile dir = locateFile(knownFiles.getRoot(), path);

            if (dir != null) {

                if (!dir.self.isDirectory())
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

        if (!dir.self.isDirectory())
            throw new NotDirectoryException(path);

        Map<String, KnownFile> children = dir.getChildrenOrNull();
        if (children != null)
            return getter.apply(fileName, children);

        dir.setTracked();

        if (dir.self.getId().isSet()) {
            for (File child : drive.getChildren(dir.self)) {
                KnownFile file = new KnownFile(child);
                knownFiles.tryAddChild(dir, file);
                knownFiles.put(file);
            }
        }

        return getter.apply(fileName, dir.getChildrenOrNull());
    }

    private void retrieveAndApplyChanges() {

        if (!applyChangesSemaphore.tryAcquire())
            return;

        try {

            DriveAdapter.Changes changes = drive.getChanges(largestChangeId + 1);

            largestChangeId = changes.largestChangeId;

            for (DriveAdapter.Change change : changes.items) {
                FileTree.this.applyChange(change);
            }
        } catch (Exception e) {
            logger.error("an error occured retrieving a list of changes", e);
            syncError.setException(e);
        } finally {
            applyChangesSemaphore.release();
        }
    }

    private void applyChange(DriveAdapter.Change change) throws Exception {

        readWriteLock.writeLock().lock();

        try {

            String changedFileId = change.fileId;
            File changedFile = change.file;

            KnownFile currentFile = knownFiles.get(new FileId(changedFileId));

            if (currentFile == null && changedFile != null && !changedFile.isTrashed()) {

                logger.debug("adding {} to tree", changedFile);

                KnownFile file = new KnownFile(changedFile);

                boolean added = false;
                for (FileId parentId : changedFile.getParentIds()) {
                    KnownFile parent = knownFiles.get(parentId);
                    if (parent != null && knownFiles.tryAddChild(parent, file))
                        added = true;
                }

                if (added)
                    knownFiles.put(file);

            } else if (currentFile != null) {

                if (changedFile == null || changedFile.isTrashed()) {

                    logger.debug("removing {} from tree", changedFile);
                    knownFiles.remove(currentFile);

                } else {

                    logger.debug("ensuring that {} is correctly placed in tree", changedFile);

                    boolean renamed = !currentFile.self.getName().equals(changedFile.getName());

                    Set<KnownFile> currentParents = currentFile.getParents();

                    Set<KnownFile> parentsToRemoveFrom = new HashSet<>(currentParents);
                    if (!renamed) {
                        for (FileId parentId : changedFile.getParentIds()) {
                            KnownFile parent = knownFiles.get(parentId);
                            if (parent != null)
                                parentsToRemoveFrom.remove(parent);
                        }
                    }

                    for (KnownFile parent : parentsToRemoveFrom) {
                        knownFiles.tryRemoveChild(parent, currentFile);
                    }

                    currentFile.self.setName(changedFile.getName());

                    Set<FileId> parentsToAddTo = new TreeSet<>(changedFile.getParentIds());
                    if (!renamed) {
                        for (KnownFile parent : currentParents) {
                            parentsToAddTo.remove(parent.self.getId());
                        }
                    }

                    for (FileId parentId : parentsToAddTo) {
                        KnownFile parent = knownFiles.get(parentId);
                        if (parent != null)
                            knownFiles.tryAddChild(parent, currentFile);
                    }

                    if (!knownFiles.removeIfHasNoParents(currentFile))
                        currentFile.self.update(changedFile);
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
