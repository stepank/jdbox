package jdbox;

import com.google.api.services.drive.Drive;
import com.google.inject.Guice;
import com.google.inject.Injector;
import sun.org.mozilla.javascript.tools.debugger.GuiCallback;

import java.io.File;

public class SetUpForTests {

    public static Injector createInjector() throws Exception {
        return JdBox.createInjector(new JdBox.Environment(new File(System.getProperty("user.home"), ".jdbox-test"), "test"));
    }

    public static void main(String[] args) throws Exception {
        createInjector().getInstance(Drive.class);
    }
}
