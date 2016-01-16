package jdbox.utils.driveadapter;

import com.google.api.services.drive.Drive;
import com.google.inject.AbstractModule;
import com.google.inject.util.Modules;
import jdbox.driveadapter.DriveAdapterModule;

public class MockDriveAdapterModule extends AbstractModule {

    private final Drive drive;

    public MockDriveAdapterModule(Drive drive) {
        this.drive = drive;
    }

    @Override
    protected void configure() {
        install(Modules.override(new DriveAdapterModule(drive)).with(new MockDriveAdapterProviderModule()));
    }
}
