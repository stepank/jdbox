package jdbox;

import com.google.common.collect.ImmutableList;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class FuzzyMountTest extends BaseMountFileSystemTest {

    private Path tempDirPath;

    private final List<ActionFactory> actionFactories = ImmutableList.of(
            new CreateFileActionFactory(),
            new CreateDirActionFactory()
    );

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

        List<Path> dirs = new ArrayList<Path>() {{
            add(Paths.get("."));
        }};
        List<Path> files = new ArrayList<>();

        for (int i = 0; i < 3; i++) {

            Action action = getNextAction(dirs, files);

            action.run(cloudRoot);
            action.run(tempDirPath);
        }

        assertThat(dumpDir(cloudRoot), equalTo(dumpDir(tempDirPath)));

        waitUntilUploaderIsDone();

        fs.unmount();

        fs = createInjector().getInstance(FileSystem.class);
        fs.mount(new java.io.File(mountPoint.toString()), false);

        assertThat(dumpDir(cloudRoot), equalTo(dumpDir(tempDirPath)));
    }

    private Action getNextAction(final List<Path> dirs, final List<Path> files) throws Exception {

        List<ActionFactory> candidates = new ArrayList<>();

        for (ActionFactory af : actionFactories) {
            if (af.canCreateAction(dirs, files)) {
                candidates.add(af);
            }
        }

        return candidates.get(new Random().nextInt(candidates.size())).createAction(dirs, files);
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

    private interface Action {
        void run(Path root) throws IOException;
    }

    private interface ActionFactory {
        boolean canCreateAction(List<Path> dirs, List<Path> files);

        Action createAction(List<Path> dirs, List<Path> files);
    }

    private class CreateFileActionFactory implements ActionFactory {

        @Override
        public boolean canCreateAction(List<Path> dirs, List<Path> files) {
            return true;
        }

        @Override
        public Action createAction(final List<Path> dirs, final List<Path> files) {
            final Path path = dirs.get(new Random().nextInt(dirs.size())).resolve(UUID.randomUUID().toString());
            files.add(path);
            return new Action() {
                @Override
                public void run(Path root) throws IOException {
                    assertThat(new File(root.resolve(path).toUri()).createNewFile(), is(true));
                }
            };
        }
    }

    private class CreateDirActionFactory implements ActionFactory {

        @Override
        public boolean canCreateAction(List<Path> dirs, List<Path> files) {
            return true;
        }

        @Override
        public Action createAction(List<Path> dirs, List<Path> files) {
            final Path path = dirs.get(new Random().nextInt(dirs.size())).resolve(UUID.randomUUID().toString());
            dirs.add(path);
            return new Action() {
                @Override
                public void run(Path root) throws IOException {
                    assertThat(new java.io.File(root.resolve(path).toUri()).mkdir(), is(true));
                }
            };
        }
    }
}