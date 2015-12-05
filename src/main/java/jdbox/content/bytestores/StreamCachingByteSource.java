package jdbox.content.bytestores;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

class StreamCachingByteSource implements ByteSource {

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

