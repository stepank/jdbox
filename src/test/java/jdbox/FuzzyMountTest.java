package jdbox;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;
import java.util.UUID;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

public class FuzzyMountTest extends BaseMountFileSystemTest {

    private Path tempDirPath;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        tempDirPath = Files.createTempDirectory("jdbox");
    }

    @After
    public void tearDown() throws Exception {
        try {
            super.tearDown();
        } finally {
            deleteDir(tempDirPath);
        }
    }

    @Test
    public void run() throws Exception {

        Path cloudRoot = mountPoint.resolve(testDir.getName());

        for (int i = 0; i < 3; i++) {

            Path relativePath = Paths.get(UUID.randomUUID().toString());
            Path cloudPath = cloudRoot.resolve(relativePath);
            Path localPath = tempDirPath.resolve(relativePath);

            new java.io.File(cloudPath.toUri()).createNewFile();
            new java.io.File(localPath.toUri()).createNewFile();
        }

        assertThat(dumpDir(cloudRoot), equalTo(dumpDir(tempDirPath)));

        waitUntilUploaderIsDone();

        fs.unmount();

        fs = createInjector().getInstance(FileSystem.class);
        fs.mount(new java.io.File(mountPoint.toString()), false);

        assertThat(dumpDir(cloudRoot), equalTo(dumpDir(tempDirPath)));
    }

    private static void deleteDir(Path path) {
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

    private static String dumpDir(Path path) {
        StringBuilder sb = new StringBuilder();
        dumpDir(path, Paths.get(""), sb);
        return sb.toString();
    }

    private static void dumpDir(Path root, Path relative, StringBuilder sb) {

        Path fullPath = root.resolve(relative);

        java.io.File file = fullPath.toFile();

        sb.append(relative.toString());

        if (!file.isDirectory()) {
            sb.append(" ").append(file.length()).append("\n");
            return;
        }

        sb.append("\n");

        java.io.File[] files = file.listFiles();
        if (files != null) {
            Arrays.sort(files, new Comparator<File>() {
                @Override
                public int compare(File a, File b) {
                    return a.compareTo(b);
                }
            });
            for (java.io.File f : files) {
                dumpDir(root, root.relativize(f.toPath()), sb);
            }
        }
    }
}