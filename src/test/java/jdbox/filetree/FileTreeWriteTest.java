package jdbox.filetree;

import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.Date;

@Category(FileTree.class)
public class FileTreeWriteTest extends BaseFileTreeWriteTest {

    /**
     * Create a file, make sure it appears.
     */
    @Test
    public void createFile() throws Exception {

        fileTree.create(testDirPath.resolve(testFileName), false);
        assertFileTreeContains().defaultEmptyTestFile().only();

        Thread.sleep(2000);
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

        Thread.sleep(2000);
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

        Thread.sleep(3000);
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

        Thread.sleep(3000);
        assertFileTreeContains(fileTree2).nothing();

        fileTree2.update();
        assertFileTreeContains(fileTree2)
                .defaultEmptyTestFile()
                .withAccessedDate(newAccessedDate)
                .withModifiedDate(newModifiedDate)
                .only();
    }
}
