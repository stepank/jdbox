package jdbox.filetree;

import com.google.inject.Module;
import jdbox.BaseTest;
import jdbox.driveadapter.DriveAdapter;
import jdbox.driveadapter.DriveAdapterModule;
import jdbox.driveadapter.File;
import jdbox.openedfiles.OpenedFilesModule;
import jdbox.uploader.UploaderModule;
import jdbox.utils.OrderedRule;
import jdbox.utils.TestFolderIsolation;
import jdbox.utils.TestFolderProvider;
import jdbox.utils.TestUtils;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Before;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class BaseFileTreeTest extends BaseTest {

    protected final static Path testDirPath = Paths.get("/");

    @OrderedRule(1)
    public TestFolderProvider testFolderProvider = new TestFolderProvider(errorCollector, injectorProvider);

    @OrderedRule(2)
    public TestFolderIsolation testFolderIsolation = new TestFolderIsolation(injectorProvider, testFolderProvider);

    protected DriveAdapter drive;
    protected FileTree fileTree;
    protected File testFolder;

    @Override
    protected Collection<Module> getRequiredModules() {
        return new ArrayList<Module>() {{
            add(new DriveAdapterModule(driveServiceProvider.getDriveService()));
            add(new UploaderModule());
            add(new OpenedFilesModule());
            add(new FileTreeModule(false));

        }};
    }

    @Before
    public void setUp() throws Exception {
        drive = injectorProvider.getInjector().getInstance(DriveAdapter.class);
        fileTree = injectorProvider.getInjector().getInstance(FileTree.class);
        testFolder = testFolderProvider.getTestFolder();
    }

    protected File createTestFile(File parent) throws Exception {
        return drive.createFile(TestUtils.testFileName, parent, TestUtils.getTestContent());
    }

    protected File createTestFileAndUpdate() throws Exception {
        return createTestFileAndUpdate(testFolderProvider.getTestFolder(), testDirPath);
    }

    protected File createTestFileAndUpdate(File parent, Path parentPath) throws Exception {
        File file = createTestFile(parent);
        fileTree.getChildren(parentPath);
        return file;
    }

    protected void assertCounts(int knownFileCount, int trackedDirCount) {
        assertCounts(fileTree, knownFileCount, trackedDirCount);
    }

    protected void assertCounts(FileTree fileTree, int knownFileCount, int trackedDirCount) {
        assertThat(fileTree.getKnownFileCount(), equalTo(knownFileCount));
        assertThat(fileTree.getTrackedDirCount(), equalTo(trackedDirCount));
    }

    protected FileTreeMatcher contains() {
        return new FileTreeMatcher().and();
    }

    protected class FileTreeMatcher extends TypeSafeMatcher<FileTree> {

        private final LinkedList<Assert> asserts = new LinkedList<>();
        private Path path = testDirPath;
        private AssertResult result;

        @Override
        public void describeTo(Description description) {
            description.appendText(result.expected);
        }

        @Override
        public void describeMismatchSafely(FileTree item, Description description) {
            description.appendText(result.actual);
        }

        @Override
        public boolean matchesSafely(FileTree fileTree) {

            try {

                List<String> children = fileTree.getChildren(path);

                if (children.size() != asserts.size()) {
                    result = new AssertResult(
                            String.format("%s file(s) in %s", asserts.size(), path),
                            String.format("%s file(s) were found", children.size()));
                    return false;
                }

                for (Assert a : asserts) {
                    result = a.check(children, fileTree.getOrNull(path.resolve(a.name)));
                    if (result != null)
                        return false;
                }

                return true;

            } catch (Exception e) {
                return false;
            }
        }

        public FileTreeMatcher nothing() {
            asserts.clear();
            return this;
        }

        public FileTreeMatcher file() {
            if (asserts.size() == 0)
                throw new IllegalStateException("this matcher is set to check that a file tree contains nothing");
            asserts.getLast().isDirectory = false;
            return this;
        }

        public FileTreeMatcher folder() {
            if (asserts.size() == 0)
                throw new IllegalStateException("this matcher is set to check that a file tree contains nothing");
            asserts.getLast().isDirectory = true;
            return this;
        }

        public FileTreeMatcher and() {
            asserts.add(new Assert());
            return this;
        }

        public FileTreeMatcher defaultTestFile() throws Exception {
            return file()
                    .withName(TestUtils.testFileName)
                    .withSize(TestUtils.testContentString.length());
        }

        public FileTreeMatcher defaultEmptyTestFile() throws Exception {
            return defaultTestFile().withSize(0);
        }

        public FileTreeMatcher defaultTestFolder() throws Exception {
            return folder()
                    .withName(TestUtils.testFolderName)
                    .withSize(0);
        }

        public FileTreeMatcher in(String path) {
            return in(Paths.get(path));
        }

        public FileTreeMatcher in(Path path) {
            if (path == null)
                throw new NullPointerException("path");
            if (path.isAbsolute())
                this.path = path;
            else
                this.path = testDirPath.resolve(path);
            return this;
        }

        public FileTreeMatcher withName(String name) {
            if (name == null)
                throw new IllegalArgumentException("name");
            asserts.getLast().name = name;
            return this;
        }

        public FileTreeMatcher withRealName(String name) {
            if (name == null)
                throw new IllegalArgumentException("name");
            asserts.getLast().realName = name;
            return this;
        }

        public FileTreeMatcher withSize(int size) {
            asserts.getLast().size = size;
            return this;
        }

        public FileTreeMatcher withAccessedDate(Date date) {
            asserts.getLast().accessedDate = date;
            return this;
        }

        public FileTreeMatcher withModifiedDate(Date date) {
            asserts.getLast().modifiedDate = date;
            return this;
        }

        public class Assert {

            public String name;
            public String realName;
            public Integer size;
            public Boolean isDirectory;
            public Date accessedDate;
            public Date modifiedDate;

            public AssertResult check(List<String> children, jdbox.models.File file) {

                if (!children.contains(name))
                    return new AssertResult(
                            String.format("file %s exists in %s", name, FileTreeMatcher.this.path),
                            String.format("file %s does not exist (it is not present in file list)", name));

                if (file == null)
                    return new AssertResult(
                            String.format("file %s exists in %s", name, FileTreeMatcher.this.path),
                            String.format("file %s does not exist (cannot retrieve its info)", name));

                if (realName == null && !file.getName().equals(name))
                    return new AssertResult(
                            String.format("%s has name %s", file, name),
                            String.format("%s has name %s", file, file.getName()));

                if (realName != null && !file.getName().equals(realName))
                    return new AssertResult(
                            String.format("%s has real name %s", file, realName),
                            String.format("%s has real name %s", file, file.getName()));

                if (size != null && file.getSize() != size)
                    return new AssertResult(
                            String.format("%s has size %s", file, size),
                            String.format("%s has size %s", file, file.getSize()));

                if (isDirectory != null && file.isDirectory() != isDirectory)
                    return new AssertResult(
                            String.format("%s is a %s", file, isDirectory ? "folder" : "file"),
                            String.format("%s is a %s", file, file.isDirectory() ? "folder" : "file"));

                if (accessedDate != null && !file.getAccessedDate().equals(accessedDate))
                    return new AssertResult(
                            String.format("%s has accessed date %s", file, accessedDate),
                            String.format("%s has accessed date %s", file, file.getAccessedDate()));

                if (modifiedDate != null && !file.getModifiedDate().equals(modifiedDate))
                    return new AssertResult(
                            String.format("%s has modified date %s", file, modifiedDate),
                            String.format("%s has modified date %s", file, file.getModifiedDate()));

                return null;
            }
        }

        private class AssertResult {

            public final String expected;
            public final String actual;

            AssertResult(String expected, String actual) {
                this.expected = expected;
                this.actual = actual;
            }
        }
    }
}
