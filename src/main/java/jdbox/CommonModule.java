package jdbox;

import com.google.inject.Injector;
import com.google.inject.Singleton;
import jdbox.models.fileids.FileIdStore;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class CommonModule extends DisposableModule {

    public static ExecutorService createExecutor() {
        return Executors.newCachedThreadPool();
    }

    @Override
    protected void configure() {
        bind(FileIdStore.class).in(Singleton.class);
        bind(ExecutorService.class).toInstance(createExecutor());
    }

    @Override
    public void dispose(Injector injector) throws Exception {
        ExecutorService executor = injector.getInstance(ExecutorService.class);
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }
}
