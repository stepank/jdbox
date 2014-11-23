package jdbox.filetree;

import com.google.common.util.concurrent.SettableFuture;
import com.google.inject.Inject;
import jdbox.DriveAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class FileTree {

    private static final Logger logger = LoggerFactory.getLogger(FileTree.class);
    private static final Path rootPath = Paths.get("/");

    private final DriveAdapter drive;
    private final ScheduledExecutorService executor;
    private final ConcurrentMap<Path, SettableFuture<ConcurrentMap<String, File>>> fileLists = new ConcurrentHashMap<>();
    private final Map<String, File> files = new HashMap<>();
    private final Map<String, Path> trackedDirs = new HashMap<>();
    private final SettableFuture syncError = SettableFuture.create();
    private final SettableFuture start = SettableFuture.create();
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

        if (start.isDone())
            return;

        DriveAdapter.BasicInfo info = drive.getBasicInfo();

        largestChangeId = info.largestChangeId;
        root = File.getRoot(info.rootFolderId);

        if (autoUpdate)
            executor.scheduleAtFixedRate(new Runnable() {
                @Override
                public void run() {
                    FileTree.this.retrieveAndApplyChanges();
                }
            }, 0, 5, TimeUnit.SECONDS);

        start.set(null);
    }

    public File getRoot() throws Exception {
        start.get();
        return root;
    }

    public void update() {

        if (autoUpdate)
            return;

        retrieveAndApplyChanges();
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

        start.get();

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
                    files.put(child.getId(), child);
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
            fileLists.get(path).get().put(file.getName(), file);
        }
    }

    private void removeFromParents(File file, Collection<String> parents) throws Exception {
        for (String parentId : parents) {
            Path path = trackedDirs.get(parentId);
            if (path == null)
                continue;
            fileLists.get(path).get().remove(file.getName());
        }
    }

    private void applyChange(DriveAdapter.Change change) throws Exception {

        synchronized (lock) {

            String changedFileId = change.fileId;
            File changedFile = change.file;

            File currentFile = files.get(changedFileId);

            if (currentFile == null && changedFile != null) {

                logger.debug("adding {} to tree", changedFile);

                files.put(changedFileId, changedFile);

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

                    files.remove(changedFileId);

                    removeFromParents(changedFile != null ? changedFile : currentFile, currentFile.getParentIds());

                    Path path = trackedDirs.get(changedFileId);

                    if (path != null) {
                        trackedDirs.remove(changedFileId);
                        fileLists.remove(path);
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

                    files.put(changedFileId, changedFile);
                }
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
