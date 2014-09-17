package jdbox;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import org.ini4j.Ini;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.Arrays;

public class JdBox {

    public static void main(String[] args) throws Exception {

        File homeDir = new File(System.getProperty("user.home") + "/.jdbox");
        Ini config = new Ini(new File(homeDir.getAbsolutePath() + "/config"));

        String mountPoint = args.length > 0 ? args[0] : config.get("Main", "mount_point");

        HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();

        FileDataStoreFactory dataStoreFactory = new FileDataStoreFactory(
                new File(homeDir.getAbsolutePath() + "/credentials"));

        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(jsonFactory,
                new InputStreamReader(JdBox.class.getResourceAsStream("/client_secrets.json")));

        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                httpTransport, jsonFactory, clientSecrets, Arrays.asList(DriveScopes.DRIVE))
                .setDataStoreFactory(dataStoreFactory)
                .setAccessType("offline")
                .build();

        Credential credential = flow.loadCredential("my");

        if (credential == null) {

            String redirectUri = "urn:ietf:wg:oauth:2.0:oob";

            String url = flow.newAuthorizationUrl().setRedirectUri(redirectUri).build();
            System.out.println("Please open the following URL in your browser then type the authorization code:");
            System.out.println("  " + url);
            BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
            String code = br.readLine();

            GoogleTokenResponse response = flow.newTokenRequest(code).setRedirectUri(redirectUri).execute();
            credential = flow.createAndStoreCredential(response, "my");
        }

        Drive drive = new Drive.Builder(httpTransport, jsonFactory, credential).setApplicationName("JDBox").build();

        new FileSystem(drive).mount(mountPoint);
    }
}
