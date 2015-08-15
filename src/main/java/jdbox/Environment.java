package jdbox;

import java.io.File;

public class Environment {

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
