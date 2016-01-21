package jdbox.uploader;

import com.google.inject.Injector;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.MapBinder;
import jdbox.modules.ActiveModule;
import rx.Observable;
import rx.Observer;
import rx.subjects.PublishSubject;
import rx.subjects.Subject;

public class UploaderModule extends ActiveModule {

    @Override
    protected void configure() {

        bind(Uploader.class).in(Singleton.class);
        MapBinder.newMapBinder(binder(), Class.class, TaskDeserializer.class);

        Subject<UploadFailureEvent, UploadFailureEvent> uploadFailureEvent = PublishSubject.create();
        bind(new TypeLiteral<Observable<UploadFailureEvent>>() {}).toInstance(uploadFailureEvent);
        bind(new TypeLiteral<Observer<UploadFailureEvent>>() {}).toInstance(uploadFailureEvent);

        Subject<FileEtagUpdateEvent, FileEtagUpdateEvent> fileEtagUpdateEvent = PublishSubject.create();
        bind(new TypeLiteral<Observable<FileEtagUpdateEvent>>() {}).toInstance(fileEtagUpdateEvent);
        bind(new TypeLiteral<Observer<FileEtagUpdateEvent>>() {}).toInstance(fileEtagUpdateEvent);
    }

    @Override
    public void init(Injector injector) {
        injector.getInstance(Uploader.class).init();
    }

    @Override
    public void start(Injector injector) {
        injector.getInstance(Uploader.class).start();
    }

    @Override
    public void tearDown(Injector injector) throws InterruptedException {
        injector.getInstance(Uploader.class).tearDown();
    }
}
