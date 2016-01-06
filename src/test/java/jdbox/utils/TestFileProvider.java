package jdbox.utils;

import jdbox.driveadapter.DriveAdapter;
import jdbox.localstate.LocalState;
import jdbox.localstate.LocalUpdateSafe;
import jdbox.localstate.knownfiles.KnownFiles;
import jdbox.models.File;
import jdbox.models.fileids.FileIdStore;
import jdbox.uploader.Uploader;
import jdbox.utils.driveadapter.Unsafe;
import org.junit.rules.ExternalResource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Random;

public class TestFileProvider extends ExternalResource {

    private final LifeCycleManagerResource lifeCycleManager;
    private final TestFolderProvider testFolderProvider;
    private final int contentLength;
    private File file;
    private byte[] content;

    public TestFileProvider(
            LifeCycleManagerResource lifeCycleManager, TestFolderProvider testFolderProvider, int contentLength) {
        this.lifeCycleManager = lifeCycleManager;
        this.testFolderProvider = testFolderProvider;
        this.contentLength = contentLength;
    }

    public File getFile() {
        return file;
    }

    public byte[] getContent() {
        return content;
    }

    public void before() throws IOException {

        content = new byte[contentLength];

        Random random = new Random();
        random.nextBytes(content);

        file = new File(
                lifeCycleManager.getInstance(FileIdStore.class),
                lifeCycleManager.getInstance(DriveAdapter.class, Unsafe.class).createFile(
                        TestUtils.testFileName, testFolderProvider.getTestFolder(),
                        new ByteArrayInputStream(content)));

        lifeCycleManager.getInstance(LocalState.class).update(new LocalUpdateSafe() {
            @Override
            public Void run(KnownFiles knownFiles, Uploader uploader) {
                knownFiles.getRoot().setTracked();
                knownFiles.getRoot().tryAddChild(knownFiles.create(file));
                return null;
            }
        });
    }
}
