package jdbox.utils;

import jdbox.FileSystem;
import org.junit.rules.ErrorCollector;
import org.junit.rules.ExternalResource;

import java.nio.file.Files;
import java.nio.file.Path;

public class MountedFileSystem extends ExternalResource {

    private final ErrorCollector errorCollector;
    private final LifeCycleManagerResource lifeCycleManager;
    private final boolean mountOnBefore;

    private Path mountPoint;

    public MountedFileSystem(ErrorCollector errorCollector, LifeCycleManagerResource lifeCycleManager) {
        this(errorCollector, lifeCycleManager, true);
    }

    public MountedFileSystem(ErrorCollector errorCollector, LifeCycleManagerResource lifeCycleManager, boolean mountOnBefore) {
        this.errorCollector = errorCollector;
        this.lifeCycleManager = lifeCycleManager;
        this.mountOnBefore = mountOnBefore;
    }

    public Path getMountPoint() {
        return mountPoint;
    }

    public void mount() throws Exception {

        mountPoint = Files.createTempDirectory("jdbox");

        lifeCycleManager.getInstance(FileSystem.class).mount(new java.io.File(mountPoint.toString()), false);
    }

    @Override
    protected void before() throws Throwable {
        if (mountOnBefore)
            mount();
    }

    @Override
    protected void after() {

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