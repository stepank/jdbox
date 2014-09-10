package jdbox;

import com.google.common.util.concurrent.SettableFuture;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;

public class FileInfoResolver {

    private final DriveAdapter drive;
    ConcurrentMap<String, SettableFuture<File>> cache = new ConcurrentHashMap<>();

    public FileInfoResolver(DriveAdapter drive) {
        this.drive = drive;
    }

    public File get(String path) throws
            ExecutionException, InterruptedException, DriveAdapter.Exception, IllegalAccessException {

        if (path.equals(java.io.File.separator)) {
            return new Root();
        }

        SettableFuture<File> future = cache.get(path);
        if (future != null)
            return future.get();

        future = SettableFuture.create();
        SettableFuture<File> existing = cache.putIfAbsent(path, future);

        if (existing != null)
            return existing.get();

        int lastSeparator = path.lastIndexOf(java.io.File.separator);
        String title = path.substring(lastSeparator + 1);
        String parentPath = path.substring(0, lastSeparator);
        if (parentPath.equals(""))
            parentPath = java.io.File.separator;

        List<File> files = drive.getChildren(get(parentPath).getId(), "title = '" + title + "'");
        File file = null;
        if (files.size() > 0)
            file = files.get(0);

        future.set(file);

        return file;
    }

    public void put(String path, File file) {
        SettableFuture<File> future = SettableFuture.create();
        cache.put(path, future);
        future.set(file);
    }
}
