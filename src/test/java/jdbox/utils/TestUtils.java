package jdbox.utils;

import com.google.api.services.drive.Drive;
import com.google.inject.Injector;
import jdbox.JdBox;
import jdbox.SetUpTests;
import jdbox.openedfiles.LocalStorage;
import jdbox.uploader.Uploader;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.lessThan;

public class TestUtils {

    public final static String testFolderName = "test_folder";
    public final static String testFileName = "test_file";
    public final static String testContentString = "hello world";

    public static Drive createDriveService() throws Exception {
        return JdBox.createDriveService(SetUpTests.createEnvironment());
    }

    public static void waitUntilUploaderIsDone(Injector injector)
            throws InterruptedException, ExecutionException, TimeoutException {
        injector.getInstance(Uploader.class).waitUntilIsDone();
    }

    public static void waitUntilLocalStorageIsEmpty(Injector injector) throws Exception {
        waitUntilUploaderIsDone(injector);
        Date start = new Date();
        while (injector.getInstance(LocalStorage.class).getFilesCount() != 0) {
            Thread.sleep(100);
            assertThat(new Date().getTime() - start.getTime(), lessThan((long) 5000));
        }
    }

    public static String getTestFolderName() {
        return testFolderName;
    }

    public static String getTestFileName() {
        return testFileName;
    }

    public static byte[] getTestContentBytes() {
        return testContentString.getBytes();
    }

    public static InputStream getTestContent() {
        return new ByteArrayInputStream(testContentString.getBytes());
    }

    public static InputStream getTestPdfContent() throws IOException {
        return JdBox.class.getResource("/test.pdf").openStream();
    }
}
