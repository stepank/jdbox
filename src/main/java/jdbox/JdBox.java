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
import com.google.inject.*;
import org.ini4j.Ini;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.Arrays;

public class JdBox {

    public static void main(String[] args) throws Exception {

        Ini config = new Ini(new File(getHomeDir(), "config"));

        createInjector()
                .getInstance(FileSystem.class)
                .mount(args.length > 0 ? args[0] : config.get("Main", "mount_point"));
    }

    public static File getHomeDir() {
        return new File(System.getProperty("user.home"), ".jdbox");
    }

    public static Injector createInjector() {
        return createInjector(new Environment(getHomeDir(), "my"));
    }

    public static Injector createInjector(Environment env) {
        return Guice.createInjector(new MainModule(env));
    }

    public static class MainModule extends AbstractModule {

        private final Environment env;

        public MainModule(Environment env) {
            this.env = env;
        }

        @Override
        protected void configure() {
            bind(Environment.class).toInstance(env);
            bind(DriveAdapter.class).in(Singleton.class);
            bind(FileInfoResolver.class).in(Singleton.class);
        }

        @Provides @Singleton
        public Drive getDriveService(Environment env) throws Exception {

            HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
            JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();

            FileDataStoreFactory dataStoreFactory = new FileDataStoreFactory(new File(env.homeDir, "credentials"));

            GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(jsonFactory,
                    new InputStreamReader(JdBox.class.getResourceAsStream("/client_secrets.json")));

            GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                    httpTransport, jsonFactory, clientSecrets, Arrays.asList(DriveScopes.DRIVE))
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

    public static class Environment {

        public final File homeDir;
        public final String userAlias;

        public Environment(File homeDir, String userAlias) {
            this.homeDir = homeDir;
            this.userAlias = userAlias;
        }
    }
}
