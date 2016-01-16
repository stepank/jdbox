package jdbox.utils.driveadapter;

import com.google.api.services.drive.Drive;
import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import com.google.inject.name.Names;
import jdbox.driveadapter.DriveAdapter;

public class UnsafeDriveAdapterModule extends AbstractModule {

    @Override
    protected void configure() {
        try {
            bind(Boolean.class).annotatedWith(Names.named("DriveAdapter.safe")).toInstance(false);
            bind(DriveAdapter.class)
                    .annotatedWith(Unsafe.class)
                    .toConstructor(DriveAdapter.class.getConstructor(Drive.class, Boolean.class))
                    .in(Singleton.class);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }
}
