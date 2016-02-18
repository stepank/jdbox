package jdbox.content;

import com.google.inject.Module;
import jdbox.BaseLifeCycleManagerTest;
import jdbox.driveadapter.DriveAdapterModule;
import jdbox.localstate.LocalStateModule;
import jdbox.uploader.UploaderModule;
import jdbox.utils.driveadapter.UnsafeDriveAdapterModule;

import java.util.ArrayList;
import java.util.List;

public class BaseContentModuleTest extends BaseLifeCycleManagerTest {

    @Override
    protected List<Module> getRequiredModules() {
        return new ArrayList<Module>() {{
            add(new DriveAdapterModule(
                    driveServiceProvider.getDriveService(), testFolderProvider.getBasicInfoProvider()));
            add(new UnsafeDriveAdapterModule());
            add(new UploaderModule());
            add(new LocalStateModule());
            add(new TestContentModule());
        }};
    }
}
