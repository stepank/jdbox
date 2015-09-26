package jdbox.filetree;

import com.google.inject.Injector;
import com.google.inject.Singleton;
import jdbox.modules.ActiveModule;

public class FileTreeModule extends ActiveModule {

    private final boolean autoUpdateFileTree;

    public FileTreeModule(boolean autoUpdateFileTree) {
        this.autoUpdateFileTree = autoUpdateFileTree;
    }

    @Override
    protected void configure() {
        bind(FileTree.class).in(Singleton.class);
    }

    @Override
    public void init(Injector injector) throws Exception {
        injector.getInstance(FileTree.class).init();
    }

    @Override
    public void start(Injector injector) {
        if (autoUpdateFileTree)
            injector.getInstance(FileTree.class).start();
    }

    @Override
    public void tearDown(Injector injector) throws Exception {
        injector.getInstance(FileTree.class).tearDown();
    }
}
