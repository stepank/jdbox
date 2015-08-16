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
import com.google.inject.Guice;
import com.google.inject.Injector;
import jdbox.driveadapter.DriveAdapterModule;
import jdbox.filetree.FileTreeModule;
import jdbox.openedfiles.OpenedFilesModule;
import jdbox.uploader.UploaderModule;
import org.ini4j.Ini;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.Collections;

public class JdBox {

    public static void main(String[] args) throws Exception {

        Environment env = new Environment(args.length > 1 ? args[1] : null, args.length > 2 ? args[2] : null);

        Injector injector = createInjector(env);

        //noinspection MismatchedQueryAndUpdateOfCollection
        Ini config = new Ini(new File(env.dataDir, "config"));
        String mountPoint = args.length > 0 ? args[0] : config.get("Main", "mount_point");

        injector.getInstance(FileSystem.class).mount(mountPoint);
    }

    public static Injector createInjector(Environment env) throws Exception {
        return Guice.createInjector(
                new CommonModule(),
                new DriveAdapterModule(createDriveService(env)),
                new UploaderModule(),
                new OpenedFilesModule(),
                new FileTreeModule(true),
                new FileSystemModule()
        );
    }

    public static Drive createDriveService(Environment env) throws Exception {

        HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();

        FileDataStoreFactory dataStoreFactory = new FileDataStoreFactory(new File(env.dataDir, "credentials"));

        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(jsonFactory,
                new InputStreamReader(JdBox.class.getResourceAsStream("/client_secrets.json")));

        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                httpTransport, jsonFactory, clientSecrets, Collections.singletonList(DriveScopes.DRIVE))
                .setDataStoreFactory(dataStoreFactory)
                .setAccessType("offline")
                .build();

        Credential credential = flow.loadCredential(env.userAlias);

        if (credential == null) {

            String redirectUri = "urn:ietf:wg:oauth:2.0:oob";

            String url = flow.newAuthorizationUrl().setRedirectUri(redirectUri).build();
            System.out.println("Please open the following URL in your browser then type the authorization code:");
            System.out.println("  " + url);
            BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
            String code = br.readLine();

            GoogleTokenResponse response = flow.newTokenRequest(code).setRedirectUri(redirectUri).execute();
            credential = flow.createAndStoreCredential(response, env.userAlias);
        }

        return new Drive.Builder(httpTransport, jsonFactory, credential).setApplicationName("JDBox").build();
    }
}
