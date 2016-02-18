package jdbox.utils;

import jdbox.driveadapter.BasicInfo;
import jdbox.driveadapter.BasicInfoProvider;

import java.io.IOException;

public class CustomRootFolderBasicInfoProvider extends BasicInfoProvider {

    private final TestFolderProvider testFolderProvider;

    public CustomRootFolderBasicInfoProvider(
            DriveServiceProvider driveServiceProvider, TestFolderProvider testFolderProvider) {
        super(driveServiceProvider.getDriveService());
        this.testFolderProvider = testFolderProvider;
    }

    @Override
    public BasicInfo getBasicInfo() throws IOException {
        return new BasicInfo(testFolderProvider.getOrCreate().getId(), super.getBasicInfo().largestChangeId);
    }
}
