package jdbox.utils;

import jdbox.driveadapter.DriveAdapter;
import jdbox.driveadapter.File;
import jdbox.utils.driveadapter.Unsafe;
import jdbox.utils.fixtures.Fixture;
import org.junit.rules.ErrorCollector;
import org.junit.rules.ExternalResource;

import java.io.IOException;
import java.util.UUID;

public class TestFolderProvider extends ExternalResource implements Fixture {

    private final ErrorCollector errorCollector;
    private final DriveServiceProvider driveServiceProvider;
    private final LifeCycleManagerResource lifeCycleManager;

    private DriveAdapter drive;
    private File testFolder;

    public TestFolderProvider(ErrorCollector errorCollector, DriveServiceProvider driveServiceProvider) {
        this.errorCollector = errorCollector;
        this.driveServiceProvider = driveServiceProvider;
        this.lifeCycleManager = null;
    }

    public TestFolderProvider(ErrorCollector errorCollector, LifeCycleManagerResource lifeCycleManager) {
        this.errorCollector = errorCollector;
        this.lifeCycleManager = lifeCycleManager;
        driveServiceProvider = null;
    }

    public File getTestFolder() {
        return testFolder;
    }

    public void before() throws IOException {
        if (driveServiceProvider != null) {
            drive = new DriveAdapter(driveServiceProvider.getDriveService(), false);
            testFolder = drive.createFolder(UUID.randomUUID().toString(), null);
        } else {
            assert lifeCycleManager != null;
            drive = lifeCycleManager.getInstance(DriveAdapter.class, Unsafe.class);
            testFolder = drive.createFolder(UUID.randomUUID().toString(), null);
            new TestFolderIsolation(lifeCycleManager, this).before();
        }
    }

    public void after() {
        try {
            drive.deleteFile(testFolder);
        } catch (IOException e) {
            errorCollector.addError(e);
        }
    }
}
