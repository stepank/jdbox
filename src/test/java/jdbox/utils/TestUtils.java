package jdbox.utils;

import com.google.api.services.drive.Drive;
import jdbox.JdBox;
import jdbox.SetUpTests;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

public class TestUtils {

    public final static String testFolderName = "test_folder";
    public final static String testFileName = "test_file";
    public final static String testContentString = "hello world";

    public static Drive createDriveService() throws Exception {
        return JdBox.createDriveService(SetUpTests.createEnvironment());
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
