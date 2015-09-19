package jdbox.filetree;

import com.google.inject.TypeLiteral;
import jdbox.openedfiles.FileSizeUpdateEvent;
import rx.Observable;

public class TestFileTreeModule extends FileTreeModule {

    public TestFileTreeModule(boolean autoUpdateFileTree) {
        super(autoUpdateFileTree);
    }

    @Override
    protected void configure() {

        super.configure();

        Observable<FileSizeUpdateEvent> fileSizeUpdateEvent = Observable.empty();
        bind(new TypeLiteral<Observable<FileSizeUpdateEvent>>() {}).toInstance(fileSizeUpdateEvent);
    }
}
