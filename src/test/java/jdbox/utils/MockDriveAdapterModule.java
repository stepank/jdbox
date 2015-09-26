package jdbox.utils;

import com.google.api.services.drive.Drive;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.util.Modules;
import jdbox.driveadapter.DriveAdapter;
import jdbox.driveadapter.DriveAdapterModule;

import java.util.concurrent.ExecutorService;

import static org.mockito.Mockito.spy;

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

class MockDriveAdapterProviderModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(DriveAdapter.class).toProvider(MockDriveAdapterProvider.class).in(Singleton.class);
    }
}

class MockDriveAdapterProvider implements Provider<DriveAdapter> {

    private final Drive drive;
    private final ExecutorService executor;

    @Inject
    public MockDriveAdapterProvider(Drive drive, ExecutorService executor) {
        this.drive = drive;
        this.executor = executor;
    }

    @Override
    public DriveAdapter get() {
        return spy(new DriveAdapter(drive, executor));
    }
}
