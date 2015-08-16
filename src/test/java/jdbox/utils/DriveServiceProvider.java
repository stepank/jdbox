package jdbox.utils;

import com.google.api.services.drive.Drive;
import org.junit.rules.ExternalResource;

public class DriveServiceProvider extends ExternalResource {

    private Drive driveService;

    public Drive getDriveService() {
        return driveService;
    }

    @Override
    protected void before() throws Throwable {
        driveService = TestUtils.createDriveService();
    }
}
