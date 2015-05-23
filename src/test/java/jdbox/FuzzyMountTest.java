package jdbox;

import com.google.common.collect.ImmutableList;
import jdbox.utils.Repeat;
import jdbox.utils.RepeatRule;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class FuzzyMountTest extends BaseMountFileSystemTest {

    @Rule
    public RepeatRule repeatRule = new RepeatRule();

    private static final Logger logger = LoggerFactory.getLogger(FuzzyMountTest.class);
    private static final Random random = new Random();

    private Path tempDirPath;

    private final List<ActionFactory> actionFactories = ImmutableList.of(
            new CreateFileActionFactory(),
            new CreateFileActionFactory(),
            new CreateDirActionFactory(),
            new MoveFileActionFactory(),
            new MoveFileActionFactory(),
            new RemoveFileActionFactory()
    );

    @Before
    public void setUp() throws Exception {
        logger.debug("entering set up");
        super.setUp();
        tempDirPath = Files.createTempDirectory("jdbox");
        logger.debug("leaving set up");
    }

    @After
    public void tearDown() throws Exception {
        logger.debug("entering tear down");
        waitUntilUploaderIsDone();
        deleteDir(tempDirPath);
        super.tearDown();
        logger.debug("leaving tear down");
    }

    @Test
    @Repeat(2)
    public void run() throws Exception {

        Path cloudRoot = mountPoint.resolve(testDir.getName());

        List<Path> dirs = new ArrayList<Path>() {{
            add(Paths.get("."));
        }};
        List<Path> files = new ArrayList<>();

        for (int i = 0; i < 20; i++) {

            Action action = getNextAction(dirs, files);

            try {
                action.run(cloudRoot);
            } catch (IOException e) {
                logger.error(
                        "an error occured while running an action in cloud, cloud directory structure is {}",
                        dumpDir(cloudRoot), e);
                throw e;
            }
            try {
                action.run(tempDirPath);
            } catch (IOException e) {
                logger.error(
                        "an error occured while running an action locally, local directory structure is {}",
                        dumpDir(tempDirPath), e);
                throw e;
            }
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

        sb.append("/").append(relative.toString());

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

    private static <T> T getRandomElement(List<T> list) {
        return list.get(random.nextInt(list.size()));
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
            final Path path = getRandomElement(dirs).resolve(UUID.randomUUID().toString().substring(0, 8));
            logger.debug("creating file {}", path);
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
            final Path path = getRandomElement(dirs).resolve(UUID.randomUUID().toString().substring(0, 8));
            logger.debug("creating dir {}", path);
            dirs.add(path);
            return new Action() {
                @Override
                public void run(Path root) throws IOException {
                    assertThat(new java.io.File(root.resolve(path).toUri()).mkdir(), is(true));
                }
            };
        }
    }

    private class MoveFileActionFactory implements ActionFactory {

        @Override
        public boolean canCreateAction(List<Path> dirs, List<Path> files) {
            return dirs.size() > 1 && files.size() > 1;
        }

        @Override
        public Action createAction(List<Path> dirs, List<Path> files) {
            final Path source = getRandomElement(files);
            final Path target = getRandomElement(dirs).resolve(source.getFileName());
            logger.debug("moving file {} to {}", source, target);
            files.remove(source);
            files.add(target);
            return new Action() {
                @Override
                public void run(Path root) throws IOException {
                    Files.move(root.resolve(source), root.resolve(target), StandardCopyOption.ATOMIC_MOVE);
                }
            };
        }
    }

    private class RemoveFileActionFactory implements ActionFactory {

        @Override
        public boolean canCreateAction(List<Path> dirs, List<Path> files) {
            return files.size() > 1;
        }

        @Override
        public Action createAction(List<Path> dirs, List<Path> files) {
            final Path path = getRandomElement(files);
            logger.debug("removing file {}", path);
            files.remove(path);
            return new Action() {
                @Override
                public void run(Path root) throws IOException {
                    Files.delete(root.resolve(path));
                }
            };
        }
    }
}
