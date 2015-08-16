package jdbox.utils;

import jdbox.driveadapter.DriveAdapter;
import jdbox.models.File;
import jdbox.models.fileids.FileIdStore;
import org.junit.rules.ExternalResource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Random;

public class TestFileProvider extends ExternalResource {

    private final InjectorProvider injectorProvider;
    private final TestFolderProvider testFolderProvider;
    private final int contentLength;
    private File file;
    private byte[] content;

    public TestFileProvider(
            InjectorProvider injectorProvider, TestFolderProvider testFolderProvider, int contentLength) {
        this.injectorProvider = injectorProvider;
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
                injectorProvider.getInjector().getInstance(FileIdStore.class),
                injectorProvider.getInjector().getInstance(DriveAdapter.class).createFile(
                        TestUtils.testFileName, testFolderProvider.getTestFolder(),
                        new ByteArrayInputStream(content)));
    }
}
