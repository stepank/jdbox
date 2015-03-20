package jdbox.filetree;

import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.SettableFuture;
import com.google.inject.Inject;
import jdbox.DriveAdapter;
import jdbox.Uploader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private volatile File root;

    private final static Getter<Map<String, File>> immutabler = new Getter<Map<String, File>>() {
        @Override
        public Map<String, File> apply(String fileName, Map<String, File> files) {
            return ImmutableMap.copyOf(files);
        }
    };

    private final static Getter<File> singleGetter = new Getter<File>() {
        @Override
        public File apply(String fileName, Map<String, File> files) {
            return files.get(fileName);
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

    public void start() throws Exception {

        if (start.getCount() == 0)
            return;

        DriveAdapter.BasicInfo info = drive.getBasicInfo();

        largestChangeId = info.largestChangeId;
        root = File.getRoot(info.rootFolderId);
        knownFiles.put(root);

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
            knownFiles.remove(root);
            knownFiles.put(file);
            root = file;
        } finally {
            readWriteLock.writeLock().unlock();
        }
    }

    public void update() {

        if (autoUpdate)
            return;

        retrieveAndApplyChanges();
    }

    public int getKnownFilesCount() {
        return knownFiles.getFileCount();
    }

    public int getTrackedDirsCount() {
        return knownFiles.getDirCount();
    }

    public File get(String path) throws Exception {
        return get(Paths.get(path));
    }

    public File get(Path path) throws Exception {

        if (path.equals(rootPath))
            return root;

        File file = getOrFetch(path.getParent(), path.getFileName().toString(), singleGetter);

        if (file == null)
            throw new NoSuchFileException(path);

        return file;
    }

    public Map<String, File> getChildren(String path) throws Exception {
        return getChildren(Paths.get(path));
    }

    public Map<String, File> getChildren(Path path) throws Exception {
        logger.debug("[{}] getting children", path);
        return getOrFetch(path, null, immutabler);
    }

    public File create(String path, boolean isDirectory) throws Exception {
        return create(Paths.get(path), isDirectory);
    }

    public File create(final Path path, boolean isDirectory) throws Exception {

        logger.debug("[{}] creating {}", path, isDirectory ? "folder" : "file");

        Path parentPath = path.getParent();
        final String fileName = path.getFileName().toString();
        final File parent = get(parentPath);

        Map<String, File> siblings = getChildren(parentPath);

        if (siblings.containsKey(fileName))
            throw new FileAlreadyExistsException(path);

        readWriteLock.writeLock().lock();

        try {

            final String tempId = UUID.randomUUID().toString();
            final File file = new File(tempId, fileName, parent.getId(), isDirectory);
            file.setCreatedDate(new Date());

            knownFiles.put(file, parent.getId());

            uploader.submit(new Runnable() {
                @Override
                public void run() {
                    applyChangesSemaphore.acquireUninterruptibly();
                    try {
                        File createdFile = drive.createFile(file, new ByteArrayInputStream(new byte[0]));
                        logger.debug("created {}", file);
                        readWriteLock.writeLock().lock();
                        try {
                            knownFiles.updateId(file, createdFile.getId());
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

            return file;

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
                    drive.touchFile(file, accessedDate, modifiedDate);
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

        Path parentPath = path.getParent();
        String fileName = path.getFileName().toString();
        final File parent = get(parentPath);

        Map<String, File> siblings = getChildren(parentPath);

        final File file = siblings.get(fileName);

        if (file == null)
            throw new NoSuchFileException(path);

        if (file.isDirectory() && getChildren(path).size() != 0)
            throw new NonEmptyDirectoryException(path);

        readWriteLock.writeLock().lock();

        try {

            knownFiles.remove(file, parent.getId());

            uploader.submit(new Runnable() {
                @Override
                public void run() {
                    applyChangesSemaphore.acquireUninterruptibly();
                    try {
                        List<String> parentIds = file.getParentIds();
                        if (!parentIds.contains(parent.getId()))
                            return;
                        if (parentIds.size() == 1)
                            drive.trashFile(file);
                        else {
                            parentIds.remove(parent.getId());
                            drive.updateParentIds(file, parentIds);
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

    private <T> T getOrFetch(Path path, String fileName, Getter<T> getter) throws Exception {

        start.await();

        if (syncError.isDone())
            syncError.get();

        KnownFile dir;

        readWriteLock.readLock().lock();
        try {

            dir = knownFiles.getDir(path);

            if (dir != null)
                return getter.apply(fileName, dir.getChildren());

        } finally {
            readWriteLock.readLock().unlock();
        }

        File file = get(path);

        if (!file.isDirectory())
            throw new NotDirectoryException(path);

        readWriteLock.writeLock().lock();

        try {

            dir = knownFiles.getDir(path);

            if (dir != null)
                return getter.apply(fileName, dir.getChildren());

            dir = knownFiles.getFile(file.getId());

            if (dir == null)
                throw new NoSuchFileException(path);

            Map<String, File> children;

            if (!dir.self.isUploaded())
                children = new HashMap<>();
            else {
                children = new HashMap<>();
                for (File child : drive.getChildren(dir.self)) {
                    children.put(child.getName(), child);
                }
            }

            knownFiles.put(children, dir, path);

            return getter.apply(fileName, children);

        } finally {
            readWriteLock.writeLock().unlock();
        }
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

            KnownFile currentFile = knownFiles.getFile(changedFileId);

            if (currentFile == null && changedFile != null && !changedFile.isTrashed()) {

                logger.debug("adding {} to tree", changedFile);
                knownFiles.put(changedFile, changedFile.getParentIds());

            } else if (currentFile != null) {

                if (changedFile != null && !currentFile.self.getName().equals(changedFile.getName())) {
                    logger.debug("renaming {} to {}", currentFile, changedFile);
                    knownFiles.rename(currentFile, changedFile);
                }

                if (changedFile == null || changedFile.isTrashed()) {

                    logger.debug("removing {} from tree", changedFile);
                    knownFiles.remove(changedFile != null ? changedFile : currentFile.self);

                } else {

                    logger.debug("ensuring that {} is correctly placed in tree", changedFile);

                    List<String> newParents = changedFile.getParentIds();

                    Set<String> parentsToAddTo = new TreeSet<>(newParents);
                    parentsToAddTo.removeAll(currentFile.self.getParentIds());
                    knownFiles.put(changedFile, parentsToAddTo);

                    Set<String> parentsToRemoveFrom = new TreeSet<>(currentFile.self.getParentIds());
                    parentsToRemoveFrom.removeAll(newParents);
                    knownFiles.remove(changedFile, parentsToRemoveFrom);

                    currentFile.self.update(changedFile);
                }
            }
        } finally {
            readWriteLock.writeLock().unlock();
        }
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
        public T apply(String fileName, Map<String, File> files);
    }
}

class KnownFiles {

    private final Map<String, KnownFile> files = new HashMap<>();
    private final Map<Path, KnownFile> dirs = new HashMap<>();

    public KnownFile getFile(File file) {
        return files.get(file.getId());
    }

    public KnownFile getFile(String id) {
        return files.get(id);
    }

    public KnownFile getDir(Path path) {
        return dirs.get(path);
    }

    public void put(File file) {
        files.put(file.getId(), new KnownFile(file));
    }

    public void put(File file, String parentId) {
        assert parentId != null;
        KnownFile kf = getFile(parentId);
        if (kf == null || !kf.addChild(file))
            return;
        kf = getFile(file);
        if (kf != null)
            kf.addParent(parentId);
        else
            files.put(file.getId(), new KnownFile(file, parentId));
    }

    public void put(File file, Collection<String> parentIds) throws Exception {
        for (String parentId : parentIds) {
            put(file, parentId);
        }
    }

    public void put(Map<String, File> files, KnownFile dir, Path path) {
        dirs.put(path, dir);
        dir.setPath(path);
        for (File file : files.values()) {
            put(file, dir.self.getId());
        }
    }

    public void remove(File file, String parentId) {
        KnownFile kf = getFile(parentId);
        if (kf != null)
            kf.removeChild(file);
        kf = getFile(file);
        if (kf.removeParent(parentId)) {
            files.remove(file.getId());
            removeDir(kf);
        }
    }

    public void remove(File file, Collection<String> parentIds) throws Exception {
        for (String parentId : parentIds) {
            remove(file, parentId);
        }
    }

    public void remove(File file) {
        KnownFile kf = files.remove(file.getId());
        removeDir(kf);
        for (String parentId : kf.getParents()) {
            getFile(parentId).removeChild(file);
        }
    }

    public void removeDir(KnownFile dir) {
        if (dir.getPath() == null)
            return;
        dirs.remove(dir.getPath());
        for (File child : dir.getChildren().values()) {
            remove(child, dir.self.getId());
        }
    }

    public void rename(KnownFile currentFile, File changedFile) {
        for (String parentId : currentFile.self.getParentIds()) {
            KnownFile kf = getFile(parentId);
            if (kf == null)
                continue;
            kf.renameChild(currentFile, changedFile);
        }
    }

    public void updateId(File file, String id) {
        KnownFile existing = files.remove(file.getId());
        existing.updateId(id);
        files.put(id, existing);
    }

    public int getFileCount() {
        return files.size();
    }

    public int getDirCount() {
        return dirs.size();
    }
}

class KnownFile {

    public final File self;
    private Path path = null;
    private Map<String, File> children = null;
    private final Set<String> parents = new HashSet<>();

    public KnownFile(File file) {
        this.self = file;
    }

    public KnownFile(File file, String parent) {
        this(file);
        this.parents.add(parent);
    }

    public Path getPath() {
        return path;
    }

    public void setPath(Path path) {
        this.path = path;
        children = new HashMap<>();
    }

    public Map<String, File> getChildren() {
        return children;
    }

    public Set<String> getParents() {
        return parents;
    }

    public void updateId(String id) {

        if (children != null) {
            for (File child : children.values()) {
                child.getParentIds().remove(self.getId());
                child.getParentIds().add(id);
            }
        }

        self.updateId(id);
    }

    public void addParent(String parent) {
        parents.add(parent);
    }

    public boolean removeParent(String parent) {
        parents.remove(parent);
        return parents.size() == 0;
    }

    public boolean addChild(File file) {
        if (children == null)
            return false;
        children.put(file.getName(), file);
        return true;
    }

    public void renameChild(KnownFile currentFile, File changedFile) {
        children.remove(currentFile.self.getName());
        children.put(changedFile.getName(), changedFile);
    }

    public boolean removeChild(File file) {
        if (children == null)
            return false;
        children.remove(file.getName());
        return true;
    }
}
