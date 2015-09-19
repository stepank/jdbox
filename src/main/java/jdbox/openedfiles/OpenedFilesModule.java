package jdbox.openedfiles;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import rx.Observable;
import rx.Observer;
import rx.subjects.BehaviorSubject;
import rx.subjects.Subject;

public class OpenedFilesModule extends AbstractModule {

    @Override
    protected void configure() {

        bind(OpenedFiles.Config.class).toInstance(OpenedFiles.defaultConfig);
        bind(OpenedFiles.class).in(Singleton.class);

        bind(NonDownloadableOpenedFileFactory.class).in(Singleton.class);

        bind(InMemoryByteStoreFactory.Config.class).toInstance(InMemoryByteStoreFactory.defaultConfig);
        bind(InMemoryByteStoreFactory.class).in(Singleton.class);

        bind(StreamCachingByteSourceFactory.Config.class).toInstance(StreamCachingByteSourceFactory.defaultConfig);
        bind(StreamCachingByteSourceFactory.class).in(Singleton.class);

        bind(FullAccessOpenedFileFactory.class).in(Singleton.class);

        bind(RollingReadOpenedFileFactory.Config.class).toInstance(RollingReadOpenedFileFactory.defaultConfig);
        bind(RollingReadOpenedFileFactory.class).in(Singleton.class);

        bind(LocalStorage.class).in(Singleton.class);

        Subject<FileSizeUpdateEvent, FileSizeUpdateEvent> fileSizeUpdateEvent = BehaviorSubject.create();
        bind(new TypeLiteral<Observable<FileSizeUpdateEvent>>() {}).toInstance(fileSizeUpdateEvent);
        bind(new TypeLiteral<Observer<FileSizeUpdateEvent>>() {}).toInstance(fileSizeUpdateEvent);
    }
}
