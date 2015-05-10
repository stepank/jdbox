package jdbox.filetree;

import com.google.inject.Injector;
import jdbox.JdBox;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.Date;

@Category(FileTree.class)
public class FileTreeWriteTest extends BaseFileTreeTest {

    protected FileTree fileTree2;

    @Before
    public void setUp() throws Exception {

        super.setUp();

        Injector injector2 = createInjector();

        fileTree2 = injector2.getInstance(FileTree.class);
        fileTree2.setRoot(testDir);

        assertFileTreeContains(fileTree2).nothing();
    }

    /**
     * Create a file, make sure it appears.
     */
    @Test
    public void createFile() throws Exception {

        fileTree.create(testDirPath.resolve(testFileName), false);
        assertFileTreeContains().defaultEmptyTestFile().only();

        waitUntilUploaderIsDone();
        assertFileTreeContains(fileTree2).nothing();

        fileTree2.update();
        assertFileTreeContains(fileTree2).defaultEmptyTestFile().only();
    }

    /**
     * Create a folder, make sure it appears.
     */
    @Test
    public void createFolder() throws Exception {

        fileTree.create(testDirPath.resolve(testFolderName), true);
        assertFileTreeContains().defaultTestFolder().only();

        waitUntilUploaderIsDone();
        assertFileTreeContains(fileTree2).nothing();

        fileTree2.update();
        assertFileTreeContains(fileTree2).defaultTestFolder().only();
    }

    /**
     * Create a folder with a file in it, make sure both appear.
     */
    @Test
    public void createFolderWithFile() throws Exception {

        fileTree.create(testDirPath.resolve(testFolderName), true);
        fileTree.create(testDirPath.resolve(testFolderName).resolve(testFileName), false);
        assertFileTreeContains().defaultTestFolder().only();
        assertFileTreeContains().in(testFolderName).defaultEmptyTestFile().only();

        waitUntilUploaderIsDone();
        assertFileTreeContains(fileTree2).nothing();

        fileTree2.update();
        assertFileTreeContains(fileTree2).defaultTestFolder().only();
        assertFileTreeContains(fileTree2).in(testFolderName).defaultEmptyTestFile().only();
    }

    /**
     * Create a file, change its accessed and modified dates, make sure they change.
     */
    @Test
    public void setDates() throws Exception {

        Date newAccessedDate = new Date(new Date().getTime() + 3600 * 1000);
        Date newModifiedDate = new Date(new Date().getTime() + 7200 * 1000);

        fileTree.create(testDirPath.resolve(testFileName), false);
        fileTree.setDates(testDirPath.resolve(testFileName), newAccessedDate, newModifiedDate);
        assertFileTreeContains()
                .defaultEmptyTestFile()
                .withAccessedDate(newAccessedDate)
                .withModifiedDate(newModifiedDate)
                .only();

        waitUntilUploaderIsDone();
        assertFileTreeContains(fileTree2).nothing();

        fileTree2.update();
        assertFileTreeContains(fileTree2)
                .defaultEmptyTestFile()
                .withAccessedDate(newAccessedDate)
                .withModifiedDate(newModifiedDate)
                .only();
    }

    /**
     * Remove a file, make sure it disappears.
     */
    @Test
    public void remove() throws Exception {

        fileTree.create(testDirPath.resolve(testFileName), false);
        assertFileTreeContains().defaultEmptyTestFile().only();

        waitUntilUploaderIsDone();
        assertFileTreeContains(fileTree2).nothing();

        fileTree2.update();
        assertFileTreeContains(fileTree2).defaultEmptyTestFile().only();

        fileTree.remove(testDirPath.resolve(testFileName));
        assertFileTreeContains().nothing();

        waitUntilUploaderIsDone();
        assertFileTreeContains(fileTree2).defaultEmptyTestFile().only();

        fileTree2.update();
        assertFileTreeContains(fileTree2).nothing();
    }

    /**
     * Move a file, make sure it disappears from the origin and appears in the destination.
     */
    @Test
    public void move() throws Exception {

        final String source = "source";
        final String destination = "destination";

        fileTree.create(testDirPath.resolve(source), true);
        fileTree.create(testDirPath.resolve(destination), true);
        fileTree.create(testDirPath.resolve(source).resolve(testFileName), false);
        assertFileTreeContains().in(source).defaultEmptyTestFile().only();
        assertFileTreeContains().in(destination).nothing();

        waitUntilUploaderIsDone();
        assertFileTreeContains(fileTree2).nothing();

        fileTree2.update();
        assertFileTreeContains(fileTree2).in(source).defaultEmptyTestFile().only();
        assertFileTreeContains(fileTree2).in(destination).nothing();

        fileTree.move(
                testDirPath.resolve(source).resolve(testFileName),
                testDirPath.resolve(destination).resolve("test_file_2"));
        assertFileTreeContains().in(source).nothing();
        assertFileTreeContains().in(destination).file().withName("test_file_2").only();

        waitUntilUploaderIsDone();
        assertFileTreeContains(fileTree2).in(source).defaultEmptyTestFile().only();
        assertFileTreeContains(fileTree2).in(destination).nothing();

        fileTree2.update();
        assertFileTreeContains(fileTree2).in(source).nothing();
        assertFileTreeContains(fileTree2).in(destination).file().withName("test_file_2").only();
    }

    /**
     * Rename a file that has a certain MIME type, but lacks extension,
     * make sure it is renamed and represented correctly.
     */
    @Test
    public void renameTyped() throws Exception {

        assertFileTreeContains().nothing();

        drive.createFile(testFileName, testDir, JdBox.class.getResource("/test.pdf").openStream());

        assertFileTreeContains().nothing();

        fileTree.update();

        assertFileTreeContains().file()
                .withName(testFileName + ".pdf")
                .withRealName(testFileName)
                .only();

        fileTree.move(
                testDirPath.resolve(testFileName + ".pdf"),
                testDirPath.resolve("test_file_2.pdf"));

        assertFileTreeContains()
                .file()
                .withName("test_file_2.pdf")
                .withRealName("test_file_2.pdf")
                .only();

        waitUntilUploaderIsDone();
        fileTree.update();

        assertFileTreeContains()
                .file()
                .withName("test_file_2.pdf")
                .withRealName("test_file_2.pdf")
                .only();

        assertCounts(2, 1);
    }
}
