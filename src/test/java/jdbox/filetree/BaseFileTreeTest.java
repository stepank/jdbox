package jdbox.filetree;

import jdbox.BaseTest;
import jdbox.driveadapter.File;
import org.junit.Before;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

public class BaseFileTreeTest extends BaseTest {

    protected Path testDirPath = Paths.get("/");
    protected FileTree fileTree;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        fileTree = injector.getInstance(FileTree.class);
        fileTree.setRoot(testDir.getId());
    }

    protected File createTestFile(File parent) throws Exception {
        return drive.createFile(testFileName, parent, getTestContent());
    }

    protected File createTestFileAndUpdate() throws Exception {
        return createTestFileAndUpdate(testDir, testDirPath);
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

    protected AssertCollection assertFileTreeContains() throws Exception {
        return new AssertCollection();
    }

    protected AssertCollection assertFileTreeContains(FileTree fileTree) throws Exception {
        return new AssertCollection(fileTree);
    }

    public class AssertCollection {

        private final LinkedList<Assert> asserts = new LinkedList<>();
        private final FileTree fileTree;
        private Path path = BaseFileTreeTest.this.testDirPath;

        public AssertCollection() {
            this(BaseFileTreeTest.this.fileTree);
        }

        public AssertCollection(FileTree fileTree) {
            this.fileTree = fileTree;
            and();
        }

        public void nothing() throws Exception {
            asserts.clear();
            only();
        }

        public AssertCollection file() {
            asserts.getLast().isDirectory = false;
            return this;
        }

        public AssertCollection folder() {
            asserts.getLast().isDirectory = true;
            return this;
        }

        public AssertCollection and() {
            asserts.add(new Assert());
            return this;
        }

        public AssertCollection defaultTestFile() throws Exception {
            return file()
                    .withName(testFileName)
                    .withSize(testContentString.length());
        }

        public AssertCollection defaultEmptyTestFile() throws Exception {
            return defaultTestFile().withSize(0);
        }

        public AssertCollection defaultTestFolder() throws Exception {
            return folder()
                    .withName(testFolderName)
                    .withSize(0);
        }

        public AssertCollection in(String path) {
            return in(Paths.get(path));
        }

        public AssertCollection in(Path path) {
            if (path == null)
                throw new IllegalArgumentException("path");
            if (path.isAbsolute())
                this.path = path;
            else
                this.path = testDirPath.resolve(path);
            return this;
        }

        public AssertCollection withName(String name) {
            if (name == null)
                throw new IllegalArgumentException("name");
            asserts.getLast().name = name;
            return this;
        }

        public AssertCollection withRealName(String name) {
            if (name == null)
                throw new IllegalArgumentException("name");
            asserts.getLast().realName = name;
            return this;
        }

        public AssertCollection withSize(int size) {
            asserts.getLast().size = size;
            return this;
        }

        public AssertCollection withAccessedDate(Date date) {
            asserts.getLast().accessedDate = date;
            return this;
        }

        public AssertCollection withModifiedDate(Date date) {
            asserts.getLast().modifiedDate = date;
            return this;
        }

        public void only() throws Exception {

            List<String> children = fileTree.getChildren(path);

            assertThat(
                    "the actual number of files does not match the expected number",
                    children.size(), equalTo(asserts.size()));

            for (Assert a : asserts) {
                children.contains(a.name);
                a.check(fileTree.getOrNull(path.resolve(a.name)));
            }
        }

        public class Assert {

            public String name;
            public String realName;
            public Integer size;
            public Boolean isDirectory;
            public Date accessedDate;
            public Date modifiedDate;

            public void check(jdbox.models.File file) {

                assertThat(String.format("file %s does not exist", name), file, notNullValue());

                if (realName == null)
                    assertThat(String.format("%s has wrong name", file), file.getName(), equalTo(name));
                else
                    assertThat(String.format("%s has wrong real name", file), file.getName(), equalTo(realName));

                if (size != null)
                    assertThat(String.format("%s has wrong size", file), file.getSize(), equalTo((long) size));

                if (isDirectory != null)
                    assertThat(
                            String.format("%s is not a %s", file, isDirectory ? "folder" : "file"),
                            file.isDirectory(), equalTo(isDirectory));

                if (accessedDate != null)
                    assertThat(String.format("%s has wrong accessed date", file), file.getAccessedDate(), equalTo(accessedDate));

                if (modifiedDate != null)
                    assertThat(String.format("%s has wrong modified date", file), file.getModifiedDate(), equalTo(modifiedDate));
            }
        }
    }
}
