package jdbox.filetree;

import com.google.inject.Injector;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import jdbox.modules.ActiveModule;
import jdbox.uploader.TaskDeserializer;

import java.io.IOException;

public class FileTreeModule extends ActiveModule {

    private final boolean autoUpdateFileTree;

    public FileTreeModule(boolean autoUpdateFileTree) {
        this.autoUpdateFileTree = autoUpdateFileTree;
    }

    @Override
    protected void configure() {

        bind(FileTree.class).in(Singleton.class);

        Multibinder<TaskDeserializer> deserializerBinder = Multibinder.newSetBinder(binder(), TaskDeserializer.class);
        deserializerBinder.addBinding().to(FileTree.class);
    }

    @Override
    public void init(Injector injector) throws IOException {
        injector.getInstance(FileTree.class).init();
    }

    @Override
    public void start(Injector injector) {
        if (autoUpdateFileTree)
            injector.getInstance(FileTree.class).start();
    }

    @Override
    public void tearDown(Injector injector) throws InterruptedException {
        injector.getInstance(FileTree.class).tearDown();
    }
}
