package jdbox.utils;

import jdbox.driveadapter.BasicInfoProvider;
import jdbox.driveadapter.DriveAdapter;
import jdbox.driveadapter.File;
import org.junit.rules.ErrorCollector;
import org.junit.rules.ExternalResource;

import java.io.IOException;
import java.util.UUID;

public class TestFolderProvider extends ExternalResource {

    private final ErrorCollector errorCollector;
    private final DriveAdapter drive;
    private final BasicInfoProvider basicInfoProvider;

    private File folder;

    public TestFolderProvider(ErrorCollector errorCollector, DriveServiceProvider driveServiceProvider) {
        this.errorCollector = errorCollector;
        drive = new DriveAdapter(driveServiceProvider.getDriveService(), false);
        basicInfoProvider = new CustomRootFolderBasicInfoProvider(driveServiceProvider, this);
    }

    public BasicInfoProvider getBasicInfoProvider() {
        return basicInfoProvider;
    }

    public File getOrCreate() throws IOException {
        if (folder == null)
            folder = drive.createFolder(UUID.randomUUID().toString(), null);
        return folder;
    }

    public void after() {

        if (folder == null)
            return;

        try {
            drive.deleteFile(folder);
            folder = null;
        } catch (IOException e) {
            errorCollector.addError(e);
        }
    }
}
