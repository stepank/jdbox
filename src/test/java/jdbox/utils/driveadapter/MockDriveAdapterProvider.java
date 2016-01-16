package jdbox.utils.driveadapter;

import com.google.api.services.drive.Drive;
import com.google.inject.Inject;
import com.google.inject.Provider;
import jdbox.driveadapter.DriveAdapter;

import static org.mockito.Mockito.spy;

class MockDriveAdapterProvider implements Provider<DriveAdapter> {

    private final Drive drive;

    @Inject
    public MockDriveAdapterProvider(Drive drive) {
        this.drive = drive;
    }

    @Override
    public DriveAdapter get() {
        return spy(new DriveAdapter(drive));
    }
}
