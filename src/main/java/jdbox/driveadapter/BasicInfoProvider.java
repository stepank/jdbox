package jdbox.driveadapter;

import com.google.api.services.drive.Drive;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class BasicInfoProvider {

    private static final Logger logger = LoggerFactory.getLogger(DriveAdapter.class);

    private Drive drive;

    @Inject
    public BasicInfoProvider(Drive drive) {
        this.drive = drive;
    }

    public BasicInfo getBasicInfo() throws IOException {

        logger.debug("getting basic info");

        return new BasicInfo(drive.about().get().execute());
    }
}
