package jdbox;

public class SetUpTests {

    public static Environment createEnvironment() {
        return new Environment(".jdbox-test", "test");
    }

    public static void main(String[] args) throws Exception {
        JdBox.createDriveService(createEnvironment());
    }
}
