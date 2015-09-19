package jdbox.filetree;

import com.google.inject.TypeLiteral;
import jdbox.openedfiles.UpdateFileSizeEvent;
import rx.Observable;

public class TestFileTreeModule extends FileTreeModule {

    public TestFileTreeModule(boolean autoUpdateFileTree) {
        super(autoUpdateFileTree);
    }

    @Override
    protected void configure() {

        super.configure();

        Observable<UpdateFileSizeEvent> updateFileSizeEvent = Observable.empty();
        bind(new TypeLiteral<Observable<UpdateFileSizeEvent>>() {}).toInstance(updateFileSizeEvent);
    }
}
