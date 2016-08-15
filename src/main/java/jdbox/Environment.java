package jdbox;

import java.nio.file.Path;
import java.nio.file.Paths;

public class Environment {

    public final Path dataDir;
    public final String userAlias;

    public Environment(String dataDirSuffix, String userAlias) {
        this.dataDir = Paths.get(System.getProperty("user.home"), dataDirSuffix != null ? dataDirSuffix : ".jdbox");
        this.userAlias = userAlias != null ? userAlias : "my";
    }
}
