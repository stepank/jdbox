package jdbox.content;

import com.google.inject.Injector;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import jdbox.content.bytestores.InMemoryByteStoreFactory;
import jdbox.content.bytestores.StreamCachingByteSourceFactory;
import jdbox.content.filetypes.FullAccessOpenedFileFactory;
import jdbox.content.filetypes.NonDownloadableOpenedFileFactory;
import jdbox.content.filetypes.RollingReadOpenedFileFactory;
import jdbox.content.localstorage.FileSizeUpdateEvent;
import jdbox.content.localstorage.LocalStorage;
import jdbox.modules.ActiveModule;
import rx.Observable;
import rx.Observer;
import rx.subjects.PublishSubject;
import rx.subjects.Subject;

import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ContentModule extends ActiveModule {

    protected volatile ThreadPoolExecutor executor;

    @Override
    protected void configure() {

        bind(OpenedFiles.class).in(Singleton.class);

        bind(OpenedFilesManager.class).to(OpenedFiles.class);

        executor = new ThreadPoolExecutor(4, 4, 60, TimeUnit.SECONDS, new LinkedBlockingDeque<Runnable>());
        executor.allowCoreThreadTimeOut(true);

        bind(Executor.class).annotatedWith(PackagePrivate.class).toInstance(executor);

        bind(OpenedFiles.Config.class).toInstance(OpenedFiles.defaultConfig);

        bind(NonDownloadableOpenedFileFactory.class).in(Singleton.class);

        bind(InMemoryByteStoreFactory.Config.class).toInstance(InMemoryByteStoreFactory.defaultConfig);
        bind(InMemoryByteStoreFactory.class).in(Singleton.class);

        bind(StreamCachingByteSourceFactory.Config.class).toInstance(StreamCachingByteSourceFactory.defaultConfig);
        bind(StreamCachingByteSourceFactory.class).in(Singleton.class);

        bind(FullAccessOpenedFileFactory.class).in(Singleton.class);

        bind(RollingReadOpenedFileFactory.Config.class).toInstance(RollingReadOpenedFileFactory.defaultConfig);
        bind(RollingReadOpenedFileFactory.class).in(Singleton.class);

        bind(LocalStorage.class).in(Singleton.class);

        Subject<FileSizeUpdateEvent, FileSizeUpdateEvent> fileSizeUpdateEvent = PublishSubject.create();
        bind(new TypeLiteral<Observable<FileSizeUpdateEvent>>() {}).toInstance(fileSizeUpdateEvent);
        bind(new TypeLiteral<Observer<FileSizeUpdateEvent>>() {}).toInstance(fileSizeUpdateEvent);
    }

    @Override
    public void tearDown(Injector injector) throws InterruptedException {
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }
}

