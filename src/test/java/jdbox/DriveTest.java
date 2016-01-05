package jdbox;

import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import jdbox.utils.TestUtils;

import java.io.IOException;
import java.util.UUID;

public class DriveTest {

    //@Test
    public void go() throws Exception {

        Drive drive = TestUtils.createDriveService();

        for (int i = 0; i < 500; i++) {

            System.out.println("running " + i);

            File file = new File()
                    .setTitle(UUID.randomUUID().toString())
                    .setMimeType("application/vnd.google-apps.folder");

            File created = drive.files().insert(file).execute();
            System.out.println("etag 1: " + created.getEtag());

            try {
                Drive.Files.Delete request = drive.files().delete(created.getId());
                request.getRequestHeaders().setIfMatch(created.getEtag());
                request.execute();
            } catch (IOException e) {
                System.out.println("etag 2: " + drive.files().get(created.getId()).execute().getEtag());
                throw e;
            }
        }
    }
}
