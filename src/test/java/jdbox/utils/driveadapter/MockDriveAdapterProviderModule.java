package jdbox.utils.driveadapter;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import jdbox.driveadapter.DriveAdapter;

class MockDriveAdapterProviderModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(DriveAdapter.class).toProvider(MockDriveAdapterProvider.class).in(Singleton.class);
    }
}
