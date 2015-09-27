package jdbox.openedfiles;

import com.google.inject.Module;
import jdbox.BaseTest;
import jdbox.driveadapter.DriveAdapterModule;
import jdbox.uploader.UploaderModule;

import java.util.ArrayList;
import java.util.List;

public class BaseOpenedFilesModuleTest extends BaseTest {

    @Override
    protected List<Module> getRequiredModules() {
        return new ArrayList<Module>() {{
            add(new DriveAdapterModule(driveServiceProvider.getDriveService()));
            add(new UploaderModule());
            add(new TestOpenedFilesModule());
        }};
    }
}
