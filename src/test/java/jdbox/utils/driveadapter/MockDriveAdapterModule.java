package jdbox.utils.driveadapter;

import com.google.api.services.drive.Drive;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.util.Modules;
import jdbox.driveadapter.BasicInfoProvider;
import jdbox.driveadapter.DriveAdapterModule;

public class MockDriveAdapterModule extends AbstractModule {

    private final Drive drive;
    private final BasicInfoProvider basicInfoProvider;

    public MockDriveAdapterModule(Drive drive, BasicInfoProvider basicInfoProvider) {
        this.drive = drive;
        this.basicInfoProvider = basicInfoProvider;
    }

    @Override
    protected void configure() {
        Module driveAdapterModule = new DriveAdapterModule(drive, basicInfoProvider);
        install(Modules.override(driveAdapterModule).with(new MockDriveAdapterProviderModule()));
    }
}
