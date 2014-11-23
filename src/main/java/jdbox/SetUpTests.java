package jdbox;

import com.google.api.services.drive.Drive;

public class SetUpTests {

    public static JdBox.Environment createEnvironment() throws Exception {
        return new JdBox.Environment(".jdbox-test", "test");
    }

    public static void main(String[] args) throws Exception {
        JdBox.createDriveService(createEnvironment());
    }
}
