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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class FileTree {

    private static final Logger logger = LoggerFactory.getLogger(FileTree.class);
    private static final Path rootPath = Paths.get("/");

    private final DriveAdapter drive;
    private final ScheduledExecutorService executor;
    private final Map<Path, Map<String, File>> fileLists = new HashMap<>();
    private final KnownFiles knownFiles = new KnownFiles();
    private final Map<String, Path> trackedDirs = new HashMap<>();
    private final SettableFuture syncError = SettableFuture.create();
    private final CountDownLatch start = new CountDownLatch(1);
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final Uploader uploader;
    private final boolean autoUpdate;

    private volatile long largestChangeId;
    private volatile File root;

    @Inject
    public FileTree(DriveAdapter drive, Uploader uploader, ScheduledExecutorService executor, boolean autoUpdate) {
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

        start.countDown();

        if (autoUpdate)
            executor.scheduleAtFixedRate(new Runnable() {
                @Override
                public void run() {
                    FileTree.this.retrieveAndApplyChanges();
                }
            }, 0, 5, TimeUnit.SECONDS);
    }

    public File getRoot() throws InterruptedException {
        start.await();
        return root;
    }

    public void setRoot(File file) throws InterruptedException {
        start.await();
        root = file;
    }

    public void update() {

        if (autoUpdate)
            return;

        retrieveAndApplyChanges();
    }

    public int getKnownFilesCount() {
        return knownFiles.count();
    }

    public int getTrackedDirsCount() {
        return trackedDirs.size();
    }

    public File get(String path) throws Exception {
        return get(Paths.get(path));
    }

    public File get(Path path) throws Exception {

        if (path.equals(rootPath))
            return root;

        File file = getChildrenInternal(path.getParent()).get(path.getFileName().toString());

        if (file == null)
            throw new NoSuchFileException(path);

        return file;
    }

    public Map<String, File> getChildren(String path) throws Exception {
        return getChildren(Paths.get(path));
    }

    public Map<String, File> getChildren(Path path) throws Exception {
        return ImmutableMap.copyOf(getChildrenInternal(path));
    }

    public void create(String path, boolean isDirectory) throws Exception {
        create(Paths.get(path), isDirectory);
    }

    public void create(final Path path, boolean isDirectory) throws Exception {

        logger.debug("[{}] creating {}", path, isDirectory ? "folder" : "file");

        Path parentPath = path.getParent();
        final String fileName = path.getFileName().toString();
        final File parent = get(parentPath);

        Map<String, File> siblings = getChildren(parentPath);

        if (siblings.containsKey(fileName))
            throw new FileAlreadyExistsException(path);

        lock.writeLock().lock();

        try {

            final String tempId = UUID.randomUUID().toString();
            final File file = new File(tempId, fileName, parent.getId(), isDirectory);
            file.setCreatedDate(new Date());

            fileLists.get(parentPath).put(fileName, file);
            knownFiles.put(file, parent.getId());

            uploader.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        File createdFile = drive.createFile(
                                file, new ByteArrayInputStream(new byte[0]));
                        logger.debug("created {}", file);
                        lock.writeLock().lock();
                        try {
                            knownFiles.remove(file, parent.getId());
                            knownFiles.put(file, parent.getId());
                            file.update(createdFile);
                            Map<String, File> children = fileLists.get(path);
                            if (children != null) {
                                for (File child : fileLists.get(path).values()) {
                                    child.getParentIds().remove(tempId);
                                    child.getParentIds().add(file.getId());
                                }
                            }
                        } finally {
                            lock.writeLock().unlock();
                        }
                    } catch (Exception e) {
                        logger.error("an error occured while creating file", e);
                    }
                }
            });

        } finally {
            lock.writeLock().unlock();
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

    private Map<String, File> getChildrenInternal(Path path) throws Exception {

        logger.debug("[{}] getting children", path);

        start.await();

        if (syncError.isDone())
            syncError.get();

        File file = get(path);

        if (file == null)
            throw new NoSuchFileException(path);

        if (!file.isDirectory())
            throw new NotDirectoryException(path);

        lock.readLock().lock();
        Map<String, File> result = fileLists.get(path);
        lock.readLock().unlock();

        if (result != null)
            return result;

        lock.writeLock().lock();

        try {

            result = fileLists.get(path);

            if (result != null)
                return result;

            trackedDirs.put(file.getId(), path);

            List<File> children;

            if (!file.isUploaded())
                result = new HashMap<>();
            else {

                try {
                    children = drive.getChildren(file);
                } catch (Exception e) {
                    trackedDirs.remove(file.getId());
                    throw e;
                }

                result = new HashMap<>();
                for (File child : children) {
                    result.put(child.getName(), child);
                    knownFiles.put(child, file.getId());
                }
            }

            fileLists.put(path, result);

            return result;

        } finally {
            lock.writeLock().unlock();
        }
    }

    private void retrieveAndApplyChanges() {

        try {

            DriveAdapter.Changes changes = drive.getChanges(largestChangeId + 1);

            largestChangeId = changes.largestChangeId;

            for (DriveAdapter.Change change : changes.items) {
                FileTree.this.applyChange(change);
            }
        } catch (Exception e) {
            logger.error("an error occured retrieving a list of changes", e);
            syncError.setException(e);
        }
    }

    private void applyChange(DriveAdapter.Change change) throws Exception {

        lock.writeLock().lock();

        try {

            String changedFileId = change.fileId;
            File changedFile = change.file;

            File currentFile = knownFiles.get(changedFileId);

            if (currentFile == null && changedFile != null && !changedFile.isTrashed()) {

                logger.debug("adding {} to tree", changedFile);

                addToParents(changedFile, changedFile.getParentIds());

            } else if (currentFile != null) {

                if (changedFile != null && !currentFile.getName().equals(changedFile.getName())) {

                    logger.debug("renaming {} to {}", currentFile, changedFile);

                    for (String parentId : currentFile.getParentIds()) {
                        Path path = trackedDirs.get(parentId);
                        if (path == null)
                            continue;
                        Map<String, File> fileList = fileLists.get(path);
                        fileList.remove(currentFile.getName());
                        fileList.put(changedFile.getName(), changedFile);
                    }
                }

                if (changedFile == null || changedFile.isTrashed()) {

                    logger.debug("removing {} from tree", changedFile);

                    removeFromParents(changedFile != null ? changedFile : currentFile, currentFile.getParentIds());

                    Path path = trackedDirs.get(changedFileId);

                    if (path != null) {
                        trackedDirs.remove(changedFileId);
                        Map<String, File> files = fileLists.remove(path);
                        for (File file : files.values()) {
                            knownFiles.remove(file, changedFileId);
                        }
                    }

                } else {

                    logger.debug("ensuring that {} is correctly placed in tree", changedFile);

                    List<String> newParents = changedFile.getParentIds();

                    Set<String> parentsToAddTo = new TreeSet<>(newParents);
                    parentsToAddTo.removeAll(currentFile.getParentIds());
                    addToParents(changedFile, parentsToAddTo);

                    Set<String> parentsToRemoveFrom = new TreeSet<>(currentFile.getParentIds());
                    parentsToRemoveFrom.removeAll(newParents);
                    removeFromParents(changedFile, parentsToRemoveFrom);

                    Set<String> remainingParents = new TreeSet<>(newParents);
                    remainingParents.removeAll(parentsToAddTo);

                    for (String parentId : remainingParents) {
                        Path path = trackedDirs.get(parentId);
                        if (path == null)
                            continue;
                        fileLists.get(path).get(changedFile.getName()).update(changedFile);
                    }
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void addToParents(File file, Collection<String> parents) throws Exception {
        for (String parentId : parents) {
            Path path = trackedDirs.get(parentId);
            if (path == null)
                continue;
            knownFiles.put(file, parentId);
            fileLists.get(path).put(file.getName(), file);
        }
    }

    private void removeFromParents(File file, Collection<String> parents) throws Exception {
        for (String parentId : parents) {
            Path path = trackedDirs.get(parentId);
            if (path == null)
                continue;
            knownFiles.remove(file, parentId);
            fileLists.get(path).remove(file.getName());
        }
    }

    private class KnownFiles {

        private final Map<String, KnownFile> files = new HashMap<>();

        public File get(String id) {
            KnownFile kf = files.get(id);
            return kf != null ? kf.file : null;
        }

        public void put(File file, String parentId) {
            KnownFile kf = files.get(file.getId());
            if (kf == null)
                files.put(file.getId(), new KnownFile(file, parentId));
            else
                files.get(file.getId()).addParent(parentId);
        }

        public void remove(File file, String parentId) {
            KnownFile kf = files.get(file.getId());
            if (kf == null)
                throw new IllegalStateException("can't remove parent from unknown file");
            if (kf.removeParent(parentId))
                files.remove(file.getId());
        }

        public int count() {
            return files.size();
        }

        private class KnownFile {

            public final File file;
            private final Set<String> parents = new HashSet<>();

            public KnownFile(File file) {
                this.file = file;
            }

            public KnownFile(File file, String parent) {
                this(file);
                this.parents.add(parent);
            }

            public void addParent(String parent) {
                parents.add(parent);
            }

            public boolean removeParent(String parent) {
                parents.remove(parent);
                return parents.size() == 0;
            }
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
}
