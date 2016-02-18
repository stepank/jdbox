package jdbox.utils;

import jdbox.FileSystem;
import jdbox.utils.fixtures.Fixture;
import org.junit.rules.ErrorCollector;
import org.junit.rules.ExternalResource;

import java.nio.file.Path;

public class MountedFileSystem extends ExternalResource implements Fixture {

    private final ErrorCollector errorCollector;
    private final TempFolderProvider tempFolderProvider;
    private final LifeCycleManagerResource lifeCycleManager;

    private Path mountPoint;

    public MountedFileSystem(
            ErrorCollector errorCollector, TempFolderProvider tempFolderProvider,
            LifeCycleManagerResource lifeCycleManager) {
        this.errorCollector = errorCollector;
        this.tempFolderProvider = tempFolderProvider;
        this.lifeCycleManager = lifeCycleManager;
    }

    public Path getMountPoint() {
        return mountPoint;
    }

    @Override
    public void before() throws Exception {
        mountPoint = tempFolderProvider.create();
        lifeCycleManager.getInstance(FileSystem.class).mount(new java.io.File(mountPoint.toString()), false);
    }

    @Override
    public void after() {

        try {
            lifeCycleManager.getInstance(FileSystem.class).unmount();
        } catch (Exception e) {
            errorCollector.addError(e);
        }

        deleteDir(mountPoint);
    }

    protected static void deleteDir(Path path) {
        java.io.File[] files = path.toFile().listFiles();
        if (files != null) {
            for (java.io.File f : files) {
                if (f.isDirectory())
                    deleteDir(f.toPath());
                else
                    //noinspection ResultOfMethodCallIgnored
                    f.delete();
            }
        }
        //noinspection ResultOfMethodCallIgnored
        path.toFile().delete();
    }
}
