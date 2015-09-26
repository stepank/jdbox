package jdbox;

import com.google.inject.Module;
import jdbox.driveadapter.DriveAdapter;
import jdbox.driveadapter.DriveAdapterModule;
import jdbox.driveadapter.File;
import jdbox.filetree.FileTreeModule;
import jdbox.openedfiles.OpenedFilesModule;
import jdbox.uploader.UploaderModule;
import jdbox.utils.OrderedRule;
import jdbox.utils.TestFolderIsolation;
import jdbox.utils.TestFolderProvider;
import org.junit.Before;

import java.util.ArrayList;
import java.util.List;

public class BaseFileSystemModuleTest extends BaseTest {

    @OrderedRule(1)
    public final TestFolderProvider testFolderProvider = new TestFolderProvider(errorCollector, lifeCycleManager);

    @OrderedRule(2)
    public final TestFolderIsolation testFolderIsolation = new TestFolderIsolation(lifeCycleManager, testFolderProvider);

    protected DriveAdapter drive;
    protected File testFolder;

    @Override
    protected List<Module> getRequiredModules() {
        return new ArrayList<Module>() {{
            add(new DriveAdapterModule(driveServiceProvider.getDriveService()));
            add(new UploaderModule());
            add(new OpenedFilesModule());
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
