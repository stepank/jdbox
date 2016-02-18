package jdbox.driveadapter;

import com.google.api.services.drive.Drive;
import com.google.inject.AbstractModule;
import com.google.inject.Singleton;

public class DriveAdapterModule extends AbstractModule {

    private final Drive drive;
    private final BasicInfoProvider basicInfoProvider;

    public DriveAdapterModule(Drive drive) {
        this.drive = drive;
        this.basicInfoProvider = null;
    }

    public DriveAdapterModule(Drive drive, BasicInfoProvider basicInfoProvider) {
        this.drive = drive;
        this.basicInfoProvider = basicInfoProvider;
    }

    @Override
    protected void configure() {

        bind(Drive.class).toInstance(drive);

        try {
            bind(DriveAdapter.class).toConstructor(DriveAdapter.class.getConstructor(Drive.class)).in(Singleton.class);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }

        if (basicInfoProvider == null)
            bind(BasicInfoProvider.class).in(Singleton.class);
        else
            bind(BasicInfoProvider.class).toInstance(basicInfoProvider);
    }
}
