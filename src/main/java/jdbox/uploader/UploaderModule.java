package jdbox.uploader;

import com.google.inject.Injector;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import jdbox.modules.ActiveModule;
import rx.Observable;
import rx.Observer;
import rx.subjects.BehaviorSubject;
import rx.subjects.Subject;

public class UploaderModule extends ActiveModule {

    @Override
    protected void configure() {

        bind(Uploader.class).in(Singleton.class);

        Subject<UploadFailureEvent, UploadFailureEvent> uploadFailureEvent = BehaviorSubject.create();
        bind(new TypeLiteral<Observable<UploadFailureEvent>>() {}).toInstance(uploadFailureEvent);
        bind(new TypeLiteral<Observer<UploadFailureEvent>>() {}).toInstance(uploadFailureEvent);
    }

    @Override
    public void init(Injector injector) {
        injector.getInstance(Uploader.class).init();
    }

    @Override
    public void tearDown(Injector injector) throws InterruptedException {
        injector.getInstance(Uploader.class).tearDown();
    }
}
