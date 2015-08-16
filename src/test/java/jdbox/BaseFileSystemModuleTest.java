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
import java.util.Collection;

public class BaseFileSystemModuleTest extends BaseTest {

    @OrderedRule(1)
    public TestFolderProvider testFolderProvider = new TestFolderProvider(errorCollector, injectorProvider);

    @OrderedRule(2)
    public TestFolderIsolation testFolderIsolation = new TestFolderIsolation(injectorProvider, testFolderProvider);

    protected DriveAdapter drive;
    protected File testFolder;

    @Override
    protected Collection<Module> getRequiredModules() {
        return new ArrayList<Module>() {{
            add(new DriveAdapterModule(driveServiceProvider.getDriveService()));
            add(new UploaderModule());
            add(new OpenedFilesModule());
            add(new FileTreeModule(true));
            add(new FileSystemModule());
        }};
    }

    @Before
    public void setUp() throws Exception {
        drive = injectorProvider.getInjector().getInstance(DriveAdapter.class);
        testFolder = testFolderProvider.getTestFolder();
    }
}
