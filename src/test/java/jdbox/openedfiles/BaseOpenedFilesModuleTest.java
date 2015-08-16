package jdbox.openedfiles;

import com.google.inject.Injector;
import com.google.inject.Module;
import jdbox.BaseTest;
import jdbox.driveadapter.DriveAdapterModule;
import jdbox.uploader.UploaderModule;
import org.junit.Before;

import java.util.ArrayList;
import java.util.Collection;

public class BaseOpenedFilesModuleTest extends BaseTest {

    protected Injector injector;

    @Override
    protected Collection<Module> getRequiredModules() {
        return new ArrayList<Module>() {{
            add(new DriveAdapterModule(driveServiceProvider.getDriveService()));
            add(new UploaderModule());
            add(new OpenedFilesModule());

        }};
    }

    @Before
    public void setUp() throws Exception {
        injector = injectorProvider.getInjector();
    }
}
