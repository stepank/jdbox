package jdbox;

import com.google.api.services.drive.Drive;
import com.google.common.util.concurrent.SettableFuture;
import com.google.inject.Injector;
import jdbox.openedfiles.OpenedFilesUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class BaseTest {

    private static JdBox.Environment env;
    private static Drive driveService;

    protected final static String testFolderName = "test_folder";
    protected final static String testFileName = "test_file";
    protected final static String testContentString = "hello world";

    protected Injector injector;

    protected DriveAdapter drive;
    protected jdbox.filetree.File testDir;

    @BeforeClass
    public static void setUpClass() throws Exception {
        env = SetUpTests.createEnvironment();
        driveService = JdBox.createDriveService(env);
    }

    @Before
    public void setUp() throws Exception {
        injector = createInjector();
        drive = injector.getInstance(DriveAdapter.class);
        testDir = drive.createFolder(UUID.randomUUID().toString());
    }

    @After
    public void tearDown() throws Exception {
        try {
            ScheduledThreadPoolExecutor executor = injector.getInstance(ScheduledThreadPoolExecutor.class);
            List<Runnable> tasks = executor.shutdownNow();
            assertThat(tasks.size(), equalTo(0));
            assertThat(executor.getActiveCount(), equalTo(0));
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } finally {
            drive.deleteFile(testDir);
        }
    }

    protected Injector createInjector() throws Exception {
        return JdBox.createInjector(env, driveService, false);
    }

    public void waitUntilUploaderIsDone() throws InterruptedException, ExecutionException, TimeoutException {
        waitUntilUploaderIsDone(5000);
    }

    public void waitUntilUploaderIsDone(long timeout)
            throws InterruptedException, ExecutionException, TimeoutException {
        final SettableFuture<Object> future = SettableFuture.create();
        injector.getInstance(Uploader.class).submit(new Runnable() {
            @Override
            public void run() {
                future.set(null);
            }
        });
        future.get(timeout, TimeUnit.SECONDS);
    }

    public void waitUntilSharedFilesAreClosed() throws Exception {
        OpenedFilesUtils.waitUntilSharedFilesAreClosed(injector);
    }

    protected static InputStream getTestContent() {
        return new ByteArrayInputStream(testContentString.getBytes());
    }
}
