package jdbox.filetree;

import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import jdbox.DisposableModule;
import jdbox.driveadapter.DriveAdapter;
import jdbox.models.fileids.FileIdStore;
import jdbox.openedfiles.UpdateFileSizeEvent;
import jdbox.uploader.Uploader;
import rx.Observable;

public class FileTreeModule extends DisposableModule {

    private final boolean autoUpdateFileTree;

    public FileTreeModule(boolean autoUpdateFileTree) {
        this.autoUpdateFileTree = autoUpdateFileTree;
    }

    @Override
    protected void configure() {
    }

    @Provides
    @Singleton
    public FileTree createFileTree(
            DriveAdapter drive, FileIdStore fileIdStore,
            Observable<UpdateFileSizeEvent> updateFileSizeEvent, Uploader uploader) throws Exception {
        FileTree ft = new FileTree(drive, fileIdStore, updateFileSizeEvent, uploader, autoUpdateFileTree);
        ft.start();
        return ft;
    }

    @Override
    public void dispose(Injector injector) throws Exception {
        injector.getInstance(FileTree.class).stopAndWait(5000);
    }
}
