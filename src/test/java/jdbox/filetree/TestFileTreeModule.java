package jdbox.filetree;

import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import jdbox.content.OpenedFilesManager;
import jdbox.content.localstorage.FileSizeUpdateEvent;
import rx.Observable;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

public class TestFileTreeModule extends FileTreeModule {

    public TestFileTreeModule(boolean autoUpdateFileTree) {
        super(autoUpdateFileTree);
    }

    @Override
    protected void configure() {

        super.configure();

        bind(OpenedFilesManager.class).to(EmptyOpenedFilesManager.class).in(Singleton.class);

        Observable<FileSizeUpdateEvent> fileSizeUpdateEventEvent = Observable.empty();
        bind(new TypeLiteral<Observable<FileSizeUpdateEvent>>() {}).toInstance(fileSizeUpdateEventEvent);
    }
}

class EmptyOpenedFilesManager implements OpenedFilesManager {

    @Override
    public int getOpenedFilesCount() {
        throw new NotImplementedException();
    }

    @Override
    public void reset() {
        throw new NotImplementedException();
    }
}
