package jdbox.content.bytestores;

import com.google.inject.Inject;

import java.io.InputStream;
import java.util.concurrent.Future;

public class StreamCachingByteSourceFactory {

    public static Config defaultConfig = new Config();

    private final InMemoryByteStoreFactory tempStoreFactory;
    private volatile Config config;

    @Inject
    StreamCachingByteSourceFactory(InMemoryByteStoreFactory tempStoreFactory, Config config) {
        this.tempStoreFactory = tempStoreFactory;
        this.config = config;
    }

    public void setConfig(Config config) {
        this.config = config;
    }

    public ByteSource create(Future<InputStream> stream, ByteStore store) {
        return new StreamCachingByteSource(stream, store, config.bufferSize);
    }

    public ByteSource create(Future<InputStream> stream) {
        return new StreamCachingByteSource(stream, tempStoreFactory.create(), config.bufferSize);
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
