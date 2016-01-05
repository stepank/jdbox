package jdbox;

import com.google.inject.Module;
import jdbox.content.ContentModule;
import jdbox.driveadapter.DriveAdapter;
import jdbox.driveadapter.DriveAdapterModule;
import jdbox.driveadapter.File;
import jdbox.filetree.FileTreeModule;
import jdbox.localstate.LocalStateModule;
import jdbox.uploader.UploaderModule;
import jdbox.utils.OrderedRule;
import jdbox.utils.TestFolderProvider;
import org.junit.Before;

import java.util.ArrayList;
import java.util.List;

public class BaseFileSystemModuleTest extends BaseTest {

    @OrderedRule
    public final TestFolderProvider testFolderProvider = new TestFolderProvider(errorCollector, lifeCycleManager);

    protected DriveAdapter drive;
    protected File testFolder;

    @Override
    protected List<Module> getRequiredModules() {
        return new ArrayList<Module>() {{
            add(new DriveAdapterModule(driveServiceProvider.getDriveService()));
            add(new UploaderModule());
            add(new LocalStateModule());
            add(new ContentModule());
            add(new FileTreeModule(true));
            add(new FileSystemModule());
        }};
    }

    @Before
    public void setUp() {
        drive = lifeCycleManager.getInstance(DriveAdapter.class);
        testFolder = testFolderProvider.getTestFolder();
    }
}
