package jdbox;

import com.google.api.services.drive.Drive;

import java.io.File;

public class SetUpForTests {

    public static Drive getTestDriveService() throws Exception {
        return JdBox.getDriveService(new File(System.getProperty("user.home") + "/.jdbox-test"), "test");
    }

    public static void main(String[] args) throws Exception {
        getTestDriveService();
    }
}
