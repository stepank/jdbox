package jdbox.openedfiles;

import com.google.inject.Injector;

import java.util.Date;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.lessThan;

public class OpenedFilesUtils {

    public static void waitUntilSharedFilesAreClosed(Injector injector) throws Exception {
        waitUntilSharedFilesAreClosed(injector, 5000);
    }

    public static void waitUntilSharedFilesAreClosed(Injector injector, long timeout) throws Exception {
        Date start = new Date();
        while (injector.getInstance(FullAccessOpenedFileFactory.class).getSharedFilesCount() != 0) {
            Thread.sleep(100);
            assertThat(new Date().getTime() - start.getTime(), lessThan(timeout));
        }
    }
}
