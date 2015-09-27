package jdbox.openedfiles;

import com.google.inject.BindingAnnotation;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import jdbox.modules.ActiveModule;
import rx.Observable;
import rx.Observer;
import rx.subjects.PublishSubject;
import rx.subjects.Subject;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class OpenedFilesModule extends ActiveModule {

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

@BindingAnnotation
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@interface PackagePrivate {}
