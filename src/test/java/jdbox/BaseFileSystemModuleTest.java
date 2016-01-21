package jdbox;

import com.google.inject.Module;
import jdbox.content.ContentModule;
import jdbox.driveadapter.DriveAdapter;
import jdbox.driveadapter.DriveAdapterModule;
import jdbox.driveadapter.File;
import jdbox.filetree.FileTreeModule;
import jdbox.localstate.LocalStateModule;
import jdbox.datapersist.DataPersistenceModule;
import jdbox.uploader.UploaderModule;
import jdbox.utils.driveadapter.Unsafe;
import jdbox.utils.driveadapter.UnsafeDriveAdapterModule;
import org.junit.Before;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class BaseFileSystemModuleTest extends BaseLifeCycleManagerTest {

    protected DriveAdapter drive;
    protected File testFolder;

    @Override
    protected List<Module> getRequiredModules() {
        return new ArrayList<Module>() {{
            add(new DriveAdapterModule(
                    driveServiceProvider.getDriveService(), testFolderProvider.getBasicInfoProvider()));
            add(new UnsafeDriveAdapterModule());
            add(new DataPersistenceModule(tempFolderProvider.create()));
            add(new UploaderModule());
            add(new LocalStateModule());
            add(new ContentModule());
            add(new FileTreeModule(true));
            add(new FileSystemModule());
        }};
    }

    @Before
    public void setUp() throws IOException {
        drive = lifeCycleManager.getInstance(DriveAdapter.class, Unsafe.class);
        testFolder = testFolderProvider.getOrCreate();
    }
}
