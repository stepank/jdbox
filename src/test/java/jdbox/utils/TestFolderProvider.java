package jdbox.utils;

import jdbox.driveadapter.DriveAdapter;
import jdbox.driveadapter.File;
import jdbox.utils.driveadapter.Unsafe;
import org.junit.rules.ErrorCollector;
import org.junit.rules.ExternalResource;

import java.io.IOException;
import java.util.UUID;

public class TestFolderProvider extends ExternalResource {

    private final ErrorCollector errorCollector;
    private final LifeCycleManagerResource lifeCycleManager;

    private DriveAdapter drive;
    private File testFolder;

    public TestFolderProvider(ErrorCollector errorCollector, LifeCycleManagerResource lifeCycleManager) {
        this.errorCollector = errorCollector;
        this.lifeCycleManager = lifeCycleManager;
    }

    public File getTestFolder() {
        return testFolder;
    }

    public void before() throws IOException {
        drive = lifeCycleManager.getInstance(DriveAdapter.class, Unsafe.class);
        testFolder = drive.createFolder(UUID.randomUUID().toString(), null);
        new TestFolderIsolation(lifeCycleManager, this).before();
    }

    public void after() {
        try {
            drive.deleteFile(testFolder);
        } catch (IOException e) {
            errorCollector.addError(e);
        }
    }
}
