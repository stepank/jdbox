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
import jdbox.driveadapter.DriveAdapter;
import jdbox.filetree.FileTree;
import jdbox.models.fileids.FileIdStore;
import jdbox.openedfiles.OpenedFilesModule;
import jdbox.uploader.Uploader;
import org.ini4j.Ini;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class JdBox {

    public static void main(String[] args) throws Exception {

        Injector injector = createInjector(args.length > 1 ? args[1] : null, args.length > 2 ? args[2] : null);
        String mountPoint = args.length > 0 ? args[0] : injector.getInstance(Ini.class).get("Main", "mount_point");

        injector.getInstance(FileSystem.class).mount(mountPoint);
    }

    public static Injector createInjector() throws Exception {
        return createInjector(new Environment());
    }

    public static Injector createInjector(String dataDirSuffix, String userAlias) throws Exception {
        return createInjector(new Environment(dataDirSuffix, userAlias));
    }

    public static Injector createInjector(Environment env) throws Exception {
        return createInjector(env, createDriveService(env));
    }

    public static Injector createInjector(Environment env, Drive drive) throws Exception {
        return createInjector(env, drive, true);
    }

    public static Injector createInjector(Environment env, Drive drive, boolean autoUpdateFileTree) throws Exception {
        return Guice.createInjector(new MainModule(env, drive, autoUpdateFileTree), new OpenedFilesModule());
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

    public static class MainModule extends AbstractModule {

        private final Environment env;
        private final Drive drive;
        private final boolean autoUpdateFileTree;
        private final Ini config;

        public MainModule(Environment env, Drive drive, boolean autoUpdateFileTree) throws Exception {
            this.env = env;
            this.drive = drive;
            this.autoUpdateFileTree = autoUpdateFileTree;
            config = new Ini(new File(env.dataDir, "config"));
        }

        @Override
        protected void configure() {

            bind(FileSystem.class).in(Singleton.class);

            bind(Environment.class).toInstance(env);
            bind(Drive.class).toInstance(drive);
            bind(Ini.class).toInstance(config);

            bind(FileIdStore.class).in(Singleton.class);
            bind(DriveAdapter.class).in(Singleton.class);
            bind(Uploader.class).in(Singleton.class);

            ThreadPoolExecutor executor =
                    new ThreadPoolExecutor(1, 8, 60, TimeUnit.SECONDS, new LinkedBlockingDeque<Runnable>());

            bind(ExecutorService.class).toInstance(executor);
            bind(ThreadPoolExecutor.class).toInstance(executor);
        }

        @Provides
        @Singleton
        public FileTree createFileTree(
                DriveAdapter drive, FileIdStore fileIdStore, Uploader uploader) throws Exception {
            FileTree ft = new FileTree(drive, fileIdStore, uploader, autoUpdateFileTree);
            ft.start();
            return ft;
        }
    }

    public static class Environment {

        public final File dataDir;
        public final String userAlias;

        public Environment() {
            this(null, null);
        }

        public Environment(String dataDirSuffix, String userAlias) {
            this.dataDir = new File(System.getProperty("user.home"), dataDirSuffix != null ? dataDirSuffix : ".jdbox");
            this.userAlias = userAlias != null ? userAlias : "my";
        }
    }
}
