package jdbox;

import com.google.api.services.drive.Drive;
import com.google.inject.Injector;
import jdbox.driveadapter.DriveAdapter;
import jdbox.driveadapter.File;
import jdbox.filetree.FileTree;
import jdbox.models.fileids.FileIdStore;
import jdbox.openedfiles.LocalStorage;
import jdbox.uploader.Uploader;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.lessThan;

public class BaseTest {

    private static JdBox.Environment env;
    private static Drive driveService;

    protected final static String testFolderName = "test_folder";
    protected final static String testFileName = "test_file";
    protected final static String testContentString = "hello world";

    protected Injector injector;

    protected DriveAdapter drive;
    protected FileIdStore fileIdStore;

    protected File testDir;
    protected boolean autoUpdateFileTree = true;

    @BeforeClass
    public static void setUpClass() throws Exception {
        env = SetUpTests.createEnvironment();
        driveService = JdBox.createDriveService(env);
    }

    @Before
    public void setUp() throws Exception {

        injector = createInjector();

        drive = injector.getInstance(DriveAdapter.class);
        fileIdStore = injector.getInstance(FileIdStore.class);

        testDir = drive.createFolder(UUID.randomUUID().toString(), null);

        injector.getInstance(FileTree.class).setRoot(testDir.getId());
    }

    @After
    public void tearDown() throws Exception {
        try {
            destroyInjector(injector);
        } finally {
            drive.deleteFile(testDir);
        }
    }

    protected Injector createInjector() throws Exception {
        return JdBox.createInjector(env, driveService, autoUpdateFileTree);
    }

    protected void destroyInjector(Injector injector) throws InterruptedException {
        injector.getInstance(FileTree.class).stopAndWait(5000);
        ExecutorService executor = injector.getInstance(ExecutorService.class);
        List<Runnable> tasks = executor.shutdownNow();
        assertThat(tasks.size(), equalTo(0));
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }

    public void waitUntilUploaderIsDone() throws InterruptedException, ExecutionException, TimeoutException {
        injector.getInstance(Uploader.class).waitUntilIsDone();
    }

    public void waitUntilLocalStorageIsEmpty() throws Exception {
        waitUntilLocalStorageIsEmpty(5000);
    }

    public void waitUntilLocalStorageIsEmpty(long timeout) throws Exception {
        waitUntilUploaderIsDone();
        Date start = new Date();
        while (injector.getInstance(LocalStorage.class).getFilesCount() != 0) {
            Thread.sleep(100);
            assertThat(new Date().getTime() - start.getTime(), lessThan(timeout));
        }
    }

    protected static InputStream getTestContent() {
        return new ByteArrayInputStream(testContentString.getBytes());
    }

    protected static InputStream getTestPdfContent() throws IOException {
        return JdBox.class.getResource("/test.pdf").openStream();
    }
}
