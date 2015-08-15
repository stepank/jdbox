package jdbox;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import jdbox.models.fileids.FileIdStore;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CommonModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(FileIdStore.class).in(Singleton.class);
        bind(ExecutorService.class).toInstance(createExecutor());
    }

    public static ExecutorService createExecutor() {
        return Executors.newCachedThreadPool();
    }
}
