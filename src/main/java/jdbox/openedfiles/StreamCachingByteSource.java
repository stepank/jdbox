package jdbox.openedfiles;

import com.google.inject.Inject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class StreamCachingByteSource implements ByteSource {

    private final byte[] buffer;

    private Future<InputStream> source;
    private ByteStore destination;
    private int available = 0;

    StreamCachingByteSource(Future<InputStream> source, ByteStore destination, int bufferSize) {
        this.source = source;
        this.destination = destination;
        this.buffer = new byte[bufferSize];
    }

    @Override
    public synchronized int read(ByteBuffer buffer, long offset, int count) throws IOException {

        if (destination == null)
            throw new IllegalStateException("read on a closed ByteSource");

        ensureStreamIsRead(offset + count);

        return destination.read(buffer, offset, count);
    }

    @Override
    public synchronized void close() throws IOException {

        if (destination == null)
            return;

        destination = null;

        closeStream();
    }

    private void closeStream() throws IOException {
        if (source != null) {
            try {
                source.get().close();
            } catch (InterruptedException | ExecutionException e) {
                checkException(e);
            }
            source = null;
        }
    }

    private void ensureStreamIsRead(long required) throws IOException {

        if (source == null)
            return;

        int read = 0;

        try {
            while (available < required && (read = source.get().read(buffer)) > -1) {
                destination.write(ByteBuffer.wrap(buffer), available, read);
                available += read;
            }
        } catch (InterruptedException | ExecutionException e) {
            checkException(e);
        }

        if (read <= -1)
            closeStream();
    }

    private void checkException(Exception e) throws IOException {
        if (e.getCause() instanceof IOException)
            throw (IOException) e.getCause();
        throw new IllegalStateException(e);
    }
}

class StreamCachingByteSourceFactory {

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