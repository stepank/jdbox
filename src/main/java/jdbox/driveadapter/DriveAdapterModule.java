package jdbox.driveadapter;

import com.google.api.services.drive.Drive;
import com.google.inject.AbstractModule;
import com.google.inject.Singleton;

public class DriveAdapterModule extends AbstractModule {

    private final Drive drive;

    public DriveAdapterModule(Drive drive) {
        this.drive = drive;
    }

    @Override
    protected void configure() {

        bind(Drive.class).toInstance(drive);

        try {
            bind(DriveAdapter.class).toConstructor(DriveAdapter.class.getConstructor(Drive.class)).in(Singleton.class);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }
}
