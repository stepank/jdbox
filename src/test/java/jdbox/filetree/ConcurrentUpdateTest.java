package jdbox.filetree;

import jdbox.driveadapter.DriveAdapter;
import jdbox.driveadapter.File;
import jdbox.uploader.Uploader;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;

import static jdbox.filetree.FileTreeMatcher.contains;
import static jdbox.utils.TestUtils.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.IsNot.not;
import static org.mockito.Matchers.notNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@Category(FileTree.class)
@RunWith(Parameterized.class)
public class ConcurrentUpdateTest extends BaseFileTreeWriteTest {

    @Parameterized.Parameter
    public FileTreeOperation operation;

    @Parameterized.Parameters
    public static Collection<Object[]> data() {

        return Arrays.asList(new Object[][]{

                {new FileTreeOperation() {

                    Date date = new Date();

                    @Override
                    public void run(FileTree fileTree) throws IOException {
                        fileTree.setDates(Paths.get("/").resolve(getTestFileName()), date, date);
                    }

                    @Override
                    public void check(FileTree fileTree) {
                        assertThat(fileTree, contains().defaultTestFile().withAccessedDate(date));
                        assertThat(fileTree, contains().defaultTestFile().withModifiedDate(date));
                    }

                    @Override
                    public void verifyCalls(DriveAdapter drive) throws IOException {
                        verify(drive, times(2)).updateFile((File) notNull());
                        verify(drive, times(1)).getFile((File) notNull());
                    }
                }},

                {new FileTreeOperation() {

                    @Override
                    public void run(FileTree fileTree) throws IOException {
                        fileTree.remove(Paths.get("/").resolve(getTestFileName()));
                    }

                    @Override
                    public void check(FileTree fileTree) {
                        assertThat(fileTree, contains().nothing());
                    }

                    @Override
                    public void verifyCalls(DriveAdapter drive) throws IOException {
                        verify(drive, times(2)).trashFile((File) notNull());
                        verify(drive, times(1)).getFile((File) notNull());
                    }
                }},

                {new FileTreeOperation() {

                    @Override
                    public void run(FileTree fileTree) throws IOException {
                        fileTree.move(Paths.get("/").resolve(getTestFileName()), Paths.get("/").resolve("hello"));
                    }

                    @Override
                    public void check(FileTree fileTree) {
                        assertThat(fileTree, contains().defaultTestFile().withName("hello"));
                    }

                    @Override
                    public void verifyCalls(DriveAdapter drive) throws IOException {
                        verify(drive, times(2)).updateFile((File) notNull());
                        verify(drive, times(1)).getFile((File) notNull());
                    }
                }}
        });
    }

    /**
     * Create a file, make sure that it is visible in the file tree.
     * Update the file directly in the cloud.
     * Try updating the file, make sure that uploader fails with 412 Precondition Failed.
     * Update another file tree, make sure that the original file is untouched.
     */
    @Test
    public void failure() throws IOException, InterruptedException {

        File file = drive.createFile(getTestFileName(), testFolder, getTestContent());

        assertThat(fileTree, contains().defaultTestFile());

        fileTree2.update();
        assertThat(fileTree2, contains().defaultTestFile());

        drive.updateFileContent(file, getTestPdfContent());

        operation.run(fileTree);

        lifeCycleManager.waitUntilUploaderIsDoneOrBroken();

        Uploader uploader = lifeCycleManager.getInstance(Uploader.class);

        assertThat(uploader.getCurrentStatus(), not(nullValue()));
        assertThat(uploader.getCurrentStatus().asString(), containsString("Upload is broken"));
        assertThat(uploader.getCurrentStatus().asString(), containsString("412 Precondition Failed"));

        fileTree2.update();
        assertThat(fileTree2, contains().defaultTestFile().withSize(getTestPdfContent().available()));
    }

    /**
     * Create a file, make sure that it is visible in the file tree.
     * Update the file directly in the cloud.
     * Update the file tree, try updating the file, make sure it is updated.
     * Update another file tree, make sure that the original is updated.
     */
    @Test
    public void success() throws IOException, InterruptedException {

        drive.createFile(getTestFileName(), testFolder, getTestContent());

        assertThat(fileTree, contains().defaultTestFile());

        fileTree2.update();
        assertThat(fileTree2, contains().defaultTestFile());

        fileTree.update();

        // now we can update the file, because its etag is up to date
        operation.run(fileTree);
        operation.check(fileTree);

        lifeCycleManager.waitUntilUploaderIsDone();

        fileTree2.update();
        operation.check(fileTree2);
    }

    /**
     * Create a file, make sure that it is visible in the file tree.
     * Update it twice (there and back) just to change etag.
     * Try updating the file, make sure it is updated.
     * Update another file tree, make sure that the original is updated.
     */
    @Test
    public void falseConflict() throws IOException, InterruptedException {

        File file = drive.createFile(getTestFileName(), testFolder, getTestContent());

        assertThat(fileTree, contains().defaultTestFile());

        fileTree2.update();
        assertThat(fileTree2, contains().defaultTestFile());

        drive.updateFileContent(file, getTestPdfContent());
        drive.updateFileContent(file, getTestContent());

        operation.run(fileTree);
        operation.check(fileTree);

        lifeCycleManager.waitUntilUploaderIsDone();

        fileTree2.update();
        operation.check(fileTree2);

        operation.verifyCalls(lifeCycleManager.getInstance(DriveAdapter.class));
    }

    private interface FileTreeOperation {

        void run(FileTree fileTree) throws IOException;

        void check(FileTree fileTree);

        void verifyCalls(DriveAdapter drive) throws IOException;
    }
}
