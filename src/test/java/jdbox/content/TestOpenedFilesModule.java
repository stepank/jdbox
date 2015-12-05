package jdbox.content;

import com.google.inject.Injector;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

public class TestOpenedFilesModule extends OpenedFilesModule {

    @Override
    public void tearDown(Injector injector) throws InterruptedException {
        List<Runnable> tasks = executor.shutdownNow();
        assertThat(tasks.size(), equalTo(0));
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }
}
