package jdbox.content.bytestores;

import com.google.inject.Inject;

public class InMemoryByteStoreFactory {

    public static Config defaultConfig = new Config();

    private volatile Config config;

    @Inject
    InMemoryByteStoreFactory(Config config) {
        this.config = config;
    }

    public void setConfig(Config config) {
        this.config = config;
    }

    public InMemoryByteStore create() {
        return new InMemoryByteStore(config.bufferSize);
    }

    public static class Config {

        public final int bufferSize;

        public Config() {
            bufferSize = 16 * 1024;
        }

        public Config(int bufferSize) {
            this.bufferSize = bufferSize;
        }
    }
}
