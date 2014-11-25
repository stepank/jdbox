package jdbox.filetree;

import com.google.common.util.concurrent.SettableFuture;
import com.google.inject.Inject;
import jdbox.DriveAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;

public class FileTree {

    private static final Logger logger = LoggerFactory.getLogger(FileTree.class);
    private static final Path rootPath = Paths.get("/");

    private final DriveAdapter drive;
    private final ScheduledExecutorService executor;
    private final ConcurrentMap<Path, SettableFuture<ConcurrentMap<String, File>>> fileLists = new ConcurrentHashMap<>();
    private final KnownFiles knownFiles = new KnownFiles();
    private final Map<String, Path> trackedDirs = new HashMap<>();
    private final SettableFuture syncError = SettableFuture.create();
    private final CountDownLatch start = new CountDownLatch(1);
    private final Object lock = new Object();
    private final boolean autoUpdate;

    private volatile long largestChangeId;
    private volatile File root;

    @Inject
    public FileTree(DriveAdapter drive, ScheduledExecutorService executor, boolean autoUpdate) {
        this.drive = drive;
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

    public Map<String, File> getChildren(String path) throws Exception {
        return getChildren(Paths.get(path));
    }

    public Map<String, File> getChildren(Path path) throws Exception {

        logger.debug("[{}] getting children", path);

        start.await();

        if (syncError.isDone())
            syncError.get();

        File file = get(path);

        if (file == null)
            throw new NoSuchFileException(path.toString());

        if (!file.isDirectory())
            throw new NotDirectoryException(path.toString());

        // This is just a small optimization: as most of the time we will have the result ready,
        // let's try to get and return it instead of creating a new future and trying to insert it.
        SettableFuture<ConcurrentMap<String, File>> future = fileLists.get(path);
        if (future != null)
            return Collections.unmodifiableMap(future.get());

        synchronized (lock) {

            future = SettableFuture.create();
            SettableFuture<ConcurrentMap<String, File>> existing = fileLists.putIfAbsent(path, future);

            if (existing != null)
                return Collections.unmodifiableMap(existing.get());

            trackedDirs.put(file.getId(), path);

            try {

                List<File> children = drive.getChildren(file);

                ConcurrentMap<String, File> result = new ConcurrentHashMap<>();
                for (File child : children) {
                    result.put(child.getName(), child);
                    knownFiles.put(child, file.getId());
                }

                future.set(result);

                return Collections.unmodifiableMap(result);

            } catch (Exception e) {
                trackedDirs.remove(file.getId());
                fileLists.remove(path).setException(e);
                throw e;
            }
        }
    }

    public File get(String path) throws Exception {
        return get(Paths.get(path));
    }

    public File get(Path path) throws Exception {

        if (path.equals(rootPath))
            return root;

        File file = getChildren(path.getParent()).get(path.getFileName().toString());

        if (file == null)
            throw new NoSuchFileException(path.toString());

        return file;
    }

    private void addToParents(File file, Collection<String> parents) throws Exception {
        for (String parentId : parents) {
            Path path = trackedDirs.get(parentId);
            if (path == null)
                continue;
            knownFiles.put(file, parentId);
            fileLists.get(path).get().put(file.getName(), file);
        }
    }

    private void removeFromParents(File file, Collection<String> parents) throws Exception {
        for (String parentId : parents) {
            Path path = trackedDirs.get(parentId);
            if (path == null)
                continue;
            knownFiles.remove(file, parentId);
            fileLists.get(path).get().remove(file.getName());
        }
    }

    private void applyChange(DriveAdapter.Change change) throws Exception {

        synchronized (lock) {

            String changedFileId = change.fileId;
            File changedFile = change.file;

            File currentFile = knownFiles.get(changedFileId);

            if (currentFile == null && changedFile != null) {

                logger.debug("adding {} to tree", changedFile);

                addToParents(changedFile, changedFile.getParentIds());

            } else if (currentFile != null) {

                if (changedFile != null && !currentFile.getName().equals(changedFile.getName())) {

                    logger.debug("renaming {} to {}", currentFile, changedFile);

                    for (String parentId : currentFile.getParentIds()) {
                        Path path = trackedDirs.get(parentId);
                        if (path == null)
                            continue;
                        ConcurrentMap<String, File> fileList = fileLists.get(path).get();
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
                        Map<String, File> files = fileLists.remove(path).get();
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
                        fileLists.get(path).get().get(changedFile.getName()).update(changedFile);
                    }
                }
            }
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
            if(kf.removeParent(parentId))
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
        public NoSuchFileException(String message) {
            super(message);
        }
    }

    public class NotDirectoryException extends Exception {
        public NotDirectoryException(String message) {
            super(message);
        }
    }
}
