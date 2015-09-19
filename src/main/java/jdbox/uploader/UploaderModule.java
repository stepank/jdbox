package jdbox.uploader;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import rx.Observable;
import rx.Observer;
import rx.subjects.BehaviorSubject;
import rx.subjects.Subject;

public class UploaderModule extends AbstractModule {

    @Override
    protected void configure() {

        bind(Uploader.class).in(Singleton.class);

        Subject<UploadFailureEvent, UploadFailureEvent> uploadFailureEvent = BehaviorSubject.create();
        bind(new TypeLiteral<Observable<UploadFailureEvent>>() {}).toInstance(uploadFailureEvent);
        bind(new TypeLiteral<Observer<UploadFailureEvent>>() {}).toInstance(uploadFailureEvent);
    }
}
