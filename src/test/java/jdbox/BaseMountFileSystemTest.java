package jdbox;

import jdbox.filetree.FileTree;
import org.junit.After;
import org.junit.Before;

import java.nio.file.Files;
import java.nio.file.Path;

public class BaseMountFileSystemTest extends BaseFileSystemTest {

    protected static Path mountPoint;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        mountPoint = Files.createTempDirectory("jdbox");
        fs.mount(new java.io.File(mountPoint.toString()), false);
    }

    @After
    public void tearDown() throws Exception {
        try {
            waitUntilLocalStorageIsEmpty();
            fs.unmount();
            deleteDir(mountPoint);
        } finally {
            super.tearDown();
        }
    }

    protected void resetFileTree() throws InterruptedException {
        injector.getInstance(FileTree.class).reset();
    }

    protected static void deleteDir(Path path) {
        java.io.File[] files = path.toFile().listFiles();
        if (files != null) {
            for (java.io.File f : files) {
                if (f.isDirectory())
                    deleteDir(f.toPath());
                else
                    //noinspection ResultOfMethodCallIgnored
                    f.delete();
            }
        }
        //noinspection ResultOfMethodCallIgnored
        path.toFile().delete();
    }
}
