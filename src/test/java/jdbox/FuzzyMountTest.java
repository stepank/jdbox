package jdbox;

import com.google.common.collect.ImmutableList;
import com.google.inject.Module;
import jdbox.content.ContentModule;
import jdbox.driveadapter.DriveAdapterModule;
import jdbox.filetree.FileTreeModule;
import jdbox.localstate.LocalStateModule;
import jdbox.uploader.UploaderModule;
import jdbox.utils.*;
import jdbox.utils.driveadapter.UnsafeDriveAdapterModule;
import jdbox.utils.fixtures.Fixture;
import jdbox.utils.fixtures.Fixtures;
import jdbox.utils.fixtures.UnsafeRunnable;
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
import java.util.concurrent.TimeUnit;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class FuzzyMountTest extends BaseTest {

    private static final Logger logger = LoggerFactory.getLogger(FuzzyMountTest.class);
    private static final Random random = new Random();

    @OrderedRule
    public final TestFolderProvider testFolderProvider = new TestFolderProvider(errorCollector, driveServiceProvider);

    private final List<ActionFactory> actionFactories = ImmutableList.of(
            new CreateFileActionFactory(),
            new CreateFileActionFactory(),
            new CreateDirActionFactory(),
            new MoveFileActionFactory(),
            new MoveFileActionFactory(),
            new RemoveFileActionFactory()
    );

    @Test(timeout = 90000)
    @Repeat(2)
    public void run() throws Throwable {

        logger.debug("starting test");

        final Path tempDirPath = tempFolderProvider.create();

        final List<Path> dirs = new ArrayList<Path>() {{
            add(Paths.get("."));
        }};
        final List<Path> files = new ArrayList<>();

        logger.debug("creating the first set of fixtures");

        final LifeCycleManagerResource lifeCycleManager =
                new LifeCycleManagerResource(errorCollector, getRequiredModules());
        TestFolderIsolation testFolderIsolation = new TestFolderIsolation(lifeCycleManager, testFolderProvider);
        final MountedFileSystem fileSystem =
                new MountedFileSystem(errorCollector, tempFolderProvider, lifeCycleManager);

        logger.debug("starting random actions in one cloud folder and in a local folder");

        Fixtures.runUnder(
                ImmutableList.<Fixture>of(lifeCycleManager, testFolderIsolation, fileSystem), new UnsafeRunnable() {
                    @Override
                    public void run() throws Exception {

                        Path mountPoint = fileSystem.getMountPoint();

                        for (int i = 0; i < 40; i++) {

                            Action action = getNextAction(dirs, files);

                            try {
                                logger.debug("running in cloud: {}", action.label);
                                action.run(mountPoint);
                                logger.debug("completed in cloud: {}", action.label);
                            } catch (IOException e) {
                                logger.error(
                                        "an error occured while running an action in cloud, " +
                                                "cloud directory structure is {}",
                                        dumpDir(mountPoint), e);
                                throw e;
                            }
                            try {
                                logger.debug("running locally: {}", action.label);
                                action.run(tempDirPath);
                                logger.debug("completed locally: {}", action.label);
                            } catch (IOException e) {
                                logger.error(
                                        "an error occured while running an action locally, " +
                                                "local directory structure is {}",
                                        dumpDir(tempDirPath), e);
                                throw e;
                            }
                        }

                        assertThat(dumpDir(mountPoint), equalTo(dumpDir(tempDirPath)));

                        lifeCycleManager.waitUntilUploaderIsDone(30, TimeUnit.SECONDS);
                    }
                });

        logger.debug("creating the second set of fixures");

        LifeCycleManagerResource lifeCycleManager2 =
                new LifeCycleManagerResource(errorCollector, getRequiredModules());
        TestFolderIsolation testFolderIsolation2 = new TestFolderIsolation(lifeCycleManager2, testFolderProvider);
        final MountedFileSystem fileSystem2 =
                new MountedFileSystem(errorCollector, tempFolderProvider, lifeCycleManager2);

        logger.debug("checking the contents of another cloud folder against the local folder");

        Fixtures.runUnder(
                ImmutableList.<Fixture>of(lifeCycleManager2, testFolderIsolation2, fileSystem2), new UnsafeRunnable() {
                    @Override
                    public void run() throws Exception {
                        fileSystem2.mount();
                        assertThat(dumpDir(fileSystem2.getMountPoint()), equalTo(dumpDir(tempDirPath)));
                    }
                });
    }

    private List<Module> getRequiredModules() {
        return new ArrayList<Module>() {{
            add(new DriveAdapterModule(driveServiceProvider.getDriveService()));
            add(new UnsafeDriveAdapterModule());
            add(new UploaderModule());
            add(new LocalStateModule());
            add(new ContentModule());
            add(new FileTreeModule(true));
            add(new FileSystemModule());
        }};
    }

    private Action getNextAction(final List<Path> dirs, final List<Path> files) {

        List<ActionFactory> candidates = new ArrayList<>();

        for (ActionFactory af : actionFactories) {
            if (af.canCreateAction(dirs, files)) {
                candidates.add(af);
            }
        }

        return candidates.get(new Random().nextInt(candidates.size())).createAction(dirs, files);
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

    private abstract class Action {

        public final String label;

        private Action(String label) {

            this.label = label;
        }

        abstract void run(Path root) throws IOException;
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
            files.add(path);
            return new Action("create file " + path) {
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
            dirs.add(path);
            return new Action("create dir " + path) {
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
            files.remove(source);
            files.add(target);
            return new Action("move file " + source + " to " + target) {
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
            files.remove(path);
            return new Action("remove file " + path) {
                @Override
                public void run(Path root) throws IOException {
                    Files.delete(root.resolve(path));
                }
            };
        }
    }
}
