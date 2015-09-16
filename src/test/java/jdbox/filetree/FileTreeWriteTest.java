package jdbox.filetree;

import jdbox.utils.LifeCycleManagerResource;
import jdbox.utils.OrderedRule;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.Date;

import static jdbox.utils.TestUtils.*;
import static org.hamcrest.MatcherAssert.assertThat;

@Category(FileTree.class)
public class FileTreeWriteTest extends BaseFileTreeTest {

    @OrderedRule
    public final LifeCycleManagerResource lifeCycleManager2 =
            new LifeCycleManagerResource(errorCollector, lifeCycleManager.getModules());

    protected FileTree fileTree2;

    @Before
    public void setUp() throws Exception {

        super.setUp();

        fileTree2 = lifeCycleManager2.getInstance(FileTree.class);
        fileTree2.setRoot(testFolder.getId());

        assertThat(fileTree2, contains().nothing());
    }

    /**
     * Create a file, make sure it appears.
     */
    @Test
    public void createFile() throws Exception {

        fileTree.create(testDirPath.resolve(getTestFileName()), false);
        assertThat(fileTree, contains().defaultEmptyTestFile());

        lifeCycleManager.waitUntilUploaderIsDone();
        assertThat(fileTree2, contains().nothing());

        fileTree2.update();
        assertThat(fileTree2, contains().defaultEmptyTestFile());
    }

    /**
     * Create a folder, make sure it appears.
     */
    @Test
    public void createFolder() throws Exception {

        fileTree.create(testDirPath.resolve(getTestFolderName()), true);
        assertThat(fileTree, contains().defaultTestFolder());

        lifeCycleManager.waitUntilUploaderIsDone();
        assertThat(fileTree2, contains().nothing());

        fileTree2.update();
        assertThat(fileTree2, contains().defaultTestFolder());
    }

    /**
     * Create a folder with a file in it, make sure both appear.
     */
    @Test
    public void createFolderWithFile() throws Exception {

        fileTree.create(testDirPath.resolve(getTestFolderName()), true);
        fileTree.create(testDirPath.resolve(getTestFolderName()).resolve(getTestFileName()), false);
        assertThat(fileTree, contains().defaultTestFolder());
        assertThat(fileTree, contains().defaultEmptyTestFile().in(getTestFolderName()));

        lifeCycleManager.waitUntilUploaderIsDone();
        assertThat(fileTree2, contains().nothing());

        fileTree2.update();
        assertThat(fileTree2, contains().defaultTestFolder());
        assertThat(fileTree2, contains().defaultEmptyTestFile().in(getTestFolderName()));
    }

    /**
     * Create a file, change its accessed and modified dates, make sure they change.
     */
    @Test
    public void setDates() throws Exception {

        Date newAccessedDate = new Date(new Date().getTime() + 3600 * 1000);
        Date newModifiedDate = new Date(new Date().getTime() + 7200 * 1000);

        fileTree.create(testDirPath.resolve(getTestFileName()), false);
        fileTree.setDates(testDirPath.resolve(getTestFileName()), newModifiedDate, newAccessedDate);
        assertThat(fileTree, contains()
                .defaultEmptyTestFile()
                .withModifiedDate(newModifiedDate)
                .withAccessedDate(newAccessedDate));

        lifeCycleManager.waitUntilUploaderIsDone();
        assertThat(fileTree2, contains().nothing());

        fileTree2.update();
        assertThat(fileTree2, contains()
                .defaultEmptyTestFile()
                .withModifiedDate(newModifiedDate)
                .withAccessedDate(newAccessedDate));
    }

    /**
     * Remove a file, make sure it disappears.
     */
    @Test
    public void remove() throws Exception {

        fileTree.create(testDirPath.resolve(getTestFileName()), false);
        assertThat(fileTree, contains().defaultEmptyTestFile());

        lifeCycleManager.waitUntilUploaderIsDone();
        assertThat(fileTree2, contains().nothing());

        fileTree2.update();
        assertThat(fileTree2, contains().defaultEmptyTestFile());

        fileTree.remove(testDirPath.resolve(getTestFileName()));
        assertThat(fileTree, contains().nothing());

        lifeCycleManager.waitUntilUploaderIsDone();
        assertThat(fileTree2, contains().defaultEmptyTestFile());

        fileTree2.update();
        assertThat(fileTree2, contains().nothing());
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
        fileTree.create(testDirPath.resolve(source).resolve(getTestFileName()), false);
        assertThat(fileTree, contains().defaultEmptyTestFile().in(source));
        assertThat(fileTree, contains().nothing().in(destination));

        lifeCycleManager.waitUntilUploaderIsDone();
        assertThat(fileTree2, contains().nothing());

        fileTree2.update();
        assertThat(fileTree2, contains().defaultEmptyTestFile().in(source));
        assertThat(fileTree2, contains().nothing().in(destination));

        fileTree.move(
                testDirPath.resolve(source).resolve(getTestFileName()),
                testDirPath.resolve(destination).resolve("test_file_2"));
        assertThat(fileTree, contains().nothing().in(source));
        assertThat(fileTree, contains().file().withName("test_file_2").in(destination));

        lifeCycleManager.waitUntilUploaderIsDone();
        assertThat(fileTree2, contains().defaultEmptyTestFile().in(source));
        assertThat(fileTree2, contains().nothing().in(destination));

        fileTree2.update();
        assertThat(fileTree2, contains().nothing().in(source));
        assertThat(fileTree2, contains().file().withName("test_file_2").in(destination));
    }

    /**
     * Rename a file that has a certain MIME type, but lacks extension,
     * make sure it is renamed and represented correctly.
     */
    @Test
    public void renameTyped() throws Exception {

        assertThat(fileTree, contains().nothing());

        drive.createFile(getTestFileName(), testFolder, getTestPdfContent());

        assertThat(fileTree, contains().nothing());

        fileTree.update();

        assertThat(fileTree, contains()
                .file()
                .withName(getTestFileName() + ".pdf")
                .withRealName(getTestFileName()));

        fileTree.move(
                testDirPath.resolve(getTestFileName() + ".pdf"),
                testDirPath.resolve("test_file_2.pdf"));

        assertThat(fileTree, contains()
                .file()
                .withName("test_file_2.pdf")
                .withRealName("test_file_2.pdf"));

        lifeCycleManager.waitUntilUploaderIsDone();
        fileTree2.update();

        assertThat(fileTree2, contains()
                .file()
                .withName("test_file_2.pdf")
                .withRealName("test_file_2.pdf"));

        assertCounts(2, 1);
    }
}
