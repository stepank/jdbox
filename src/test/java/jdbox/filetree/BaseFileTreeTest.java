package jdbox.filetree;

import jdbox.BaseTest;
import jdbox.DriveAdapter;
import org.junit.After;
import org.junit.Before;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.LinkedList;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

public class BaseFileTreeTest extends BaseTest {

    protected final static String testFolderName = "test_folder";
    protected final static String testFileName = "test_file";
    protected final static String testContentString = "hello world";

    protected DriveAdapter drive;
    protected FileTree fileTree;
    protected Path testDirPath;
    protected jdbox.filetree.File testDir;

    @Before
    public void setUp() throws Exception {

        super.setUp();

        drive = injector.getInstance(DriveAdapter.class);
        fileTree = injector.getInstance(FileTree.class);

        String testDirName = UUID.randomUUID().toString();
        testDirPath = Paths.get("/");
        testDir = drive.createFolder(testDirName, fileTree.getRoot());

        fileTree.setRoot(testDir);
    }

    @After
    public void tearDown() throws Exception {
        drive.deleteFile(testDir);
    }

    protected jdbox.filetree.File createTestFile(jdbox.filetree.File parent) throws Exception {
        return drive.createFile(testFileName, parent, getTestContent());
    }

    protected jdbox.filetree.File createTestFileAndUpdate() throws Exception {
        return createTestFileAndUpdate(testDir, testDirPath);
    }

    protected jdbox.filetree.File createTestFileAndUpdate(jdbox.filetree.File parent, Path parentPath) throws Exception {
        createTestFile(parent);
        return fileTree.getChildren(parentPath).get(testFileName);
    }

    protected void assertCounts(int knownFilesCount, int trackedDirsCount) {
        assertCounts(fileTree, knownFilesCount, trackedDirsCount);
    }

    protected void assertCounts(FileTree fileTree, int knownFilesCount, int trackedDirsCount) {
        assertThat(fileTree.getKnownFilesCount(), equalTo(knownFilesCount));
        assertThat(fileTree.getTrackedDirsCount(), equalTo(trackedDirsCount));
    }

    protected static InputStream getTestContent() {
        return new ByteArrayInputStream(testContentString.getBytes());
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
        private Integer count = null;

        public AssertCollection() {
            this(BaseFileTreeTest.this.fileTree);
        }

        public AssertCollection(FileTree fileTree) {
            this.fileTree = fileTree;
            and();
        }

        public void nothing() throws Exception {
            count = 0;
            check();
        }

        public void only() throws Exception {
            count = 1;
            check();
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

        public void check() throws Exception {

            Map<String, jdbox.filetree.File> children = fileTree.getChildren(path);

            if (count != null) {
                if (count.equals(0)) {
                    assertThat(children.size(), equalTo(0));
                    return;
                } else
                    assertThat(children.size(), equalTo(count));
            }

            for (Assert a : asserts) {
                a.check(children.get(a.name));
            }
        }

        public class Assert {

            public String name;
            public Integer size;
            public Boolean isDirectory;
            public Date accessedDate;
            public Date modifiedDate;

            public void check(File file) {

                assertThat(String.format("file %s does not exist", name), file, notNullValue());

                assertThat(String.format("%s has wrong name", file), file.getName(), equalTo(name));

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
