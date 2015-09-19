package jdbox;

import com.google.api.services.drive.Drive;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.util.Modules;
import jdbox.driveadapter.DriveAdapter;
import jdbox.driveadapter.DriveAdapterModule;
import jdbox.driveadapter.File;
import jdbox.filetree.FileTree;
import jdbox.filetree.FileTreeModule;
import jdbox.openedfiles.LocalStorage;
import jdbox.openedfiles.OpenedFilesModule;
import jdbox.uploader.Uploader;
import jdbox.uploader.UploaderModule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;

import static jdbox.utils.TestUtils.getTestContentBytes;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class UploadFailureTest extends BaseMountFileSystemTest {

    private static final Logger logger = LoggerFactory.getLogger(UploadFailureTest.class);

    @Override
    protected List<Module> getRequiredModules() {
        return new ArrayList<Module>() {{
            add(Modules
                    .override(new DriveAdapterModule(driveServiceProvider.getDriveService()))
                    .with(new FailingDriveAdapterModule()));
            add(new UploaderModule());
            add(new OpenedFilesModule());
            add(new FileTreeModule(true));
            add(new FileSystemModule());
        }};
    }

    @Test
    public void fullFlow() throws Exception {

        Path uploadNotificationFilePath = mountPoint.resolve(FileTree.uploadNotificationFileName);

        logger.debug("make sure that upload notification file does not exist");
        assertThat(Files.exists(uploadNotificationFilePath), is(false));

        logger.debug("create some file for the future");
        Files.write(mountPoint.resolve("test.txt"), getTestContentBytes());
        assertThat(Files.readAllBytes(mountPoint.resolve("test.txt")), equalTo(getTestContentBytes()));

        logger.debug("wait until it is uploaded");
        lifeCycleManager.waitUntilUploaderIsDone();

        logger.debug("make the DriveAdapter throw exceptions on content update");
        FailingDriveAdapter drive = (FailingDriveAdapter) lifeCycleManager.getInstance(DriveAdapter.class);
        drive.exception = new IOException("something bad happened");

        logger.debug("write some data to another file");
        Files.write(mountPoint.resolve("test2.txt"), getTestContentBytes());
        assertThat(Files.readAllBytes(mountPoint.resolve("test2.txt")), equalTo(getTestContentBytes()));

        logger.debug("wait until uploader breaks down trying to upload the written content to the cloud");
        lifeCycleManager.waitUntilUploaderIsDoneOrBroken();

        logger.debug("check that upload notification file appeared");
        assertThat(Files.exists(uploadNotificationFilePath), is(true));

        logger.debug("check its modification date");
        java.io.File uploadNotificationFile = uploadNotificationFilePath.toFile();
        assertThat(
                // the accuracy of modification date is seconds
                new Date().getTime() / 1000 - uploadNotificationFile.lastModified() / 1000,
                // 1 is because upload failure and assertion can happen in different seconds
                lessThanOrEqualTo((long) 1));

        logger.debug("check its content");
        String content = new String(Files.readAllBytes(mountPoint.resolve(uploadNotificationFilePath)));
        assertThat(content.indexOf("Upload is broken"), greaterThanOrEqualTo(0));
        assertThat(content.indexOf("something bad happened"), greaterThanOrEqualTo(0));

        logger.debug("wait do the clock to advance a little");
        Thread.sleep(2000);

        logger.debug("upload some more data to another file");
        Files.write(mountPoint.resolve("test3.txt"), getTestContentBytes());
        assertThat(Files.readAllBytes(mountPoint.resolve("test3.txt")), equalTo(getTestContentBytes()));

        logger.debug("wait until uploader updates its status");
        // waitUntilUploaderIsDoneOrBroken will cannot be used because no task will be submitted to the executor,
        // so waitUntilUploaderIsDoneOrBroken will return immediately
        Thread.sleep(500);

        logger.debug("check upload notification file's mofitication date again");
        assertThat(
                // the accuracy of modification date is seconds
                new Date().getTime() / 1000 - uploadNotificationFile.lastModified() / 1000,
                // 1 is because upload failure and assertion can happen in different seconds
                lessThanOrEqualTo((long) 1));

        logger.debug("delete upload notification file");
        //noinspection ResultOfMethodCallIgnored
        uploadNotificationFile.delete();

        logger.debug("check that the local state is empty");
        assertThat(lifeCycleManager.getInstance(FileTree.class).getKnownFileCount(), equalTo(1));
        assertThat(lifeCycleManager.getInstance(FileTree.class).getTrackedDirCount(), equalTo(0));
        assertThat(lifeCycleManager.getInstance(LocalStorage.class).getFilesCount(), equalTo(0));
        assertThat(lifeCycleManager.getInstance(Uploader.class).getQueueCount(), equalTo(0));

        logger.debug("repair the DriveAdapter");
        drive.exception = null;

        logger.debug("check that upload notification file does not exist");
        assertThat(Files.exists(uploadNotificationFilePath), is(false));

        logger.debug("check that the first created file exists");
        assertThat(Files.readAllBytes(mountPoint.resolve("test.txt")), equalTo(getTestContentBytes()));

        logger.debug("update its content");
        Files.write(mountPoint.resolve("test.txt"), new byte[]{1, 2, 3});

        logger.debug("wait until it is uploaded");
        lifeCycleManager.waitUntilUploaderIsDone();

        logger.debug("reset local state");
        lifeCycleManager.getInstance(FileTree.class).reset();
        lifeCycleManager.getInstance(LocalStorage.class).reset();
        lifeCycleManager.getInstance(Uploader.class).reset();

        logger.debug("check that the first created file is present and has the new content");
        assertThat(Files.readAllBytes(mountPoint.resolve("test.txt")), equalTo(new byte[]{1, 2, 3}));
    }

    private class FailingDriveAdapterModule extends AbstractModule {

        @Override
        protected void configure() {
            bind(DriveAdapter.class).to(FailingDriveAdapter.class).in(Singleton.class);
        }
    }
}

class FailingDriveAdapter extends DriveAdapter {

    public volatile IOException exception;

    @Inject
    public FailingDriveAdapter(Drive drive, ExecutorService executor) {
        super(drive, executor);
    }

    @Override
    public File updateFileContent(File file, InputStream content) throws IOException {
        if (exception != null)
            throw exception;
        return super.updateFileContent(file, content);
    }
}
