package jdbox.openedfiles;

import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

class InMemoryByteStore implements ByteStore {

    private static final Logger logger = LoggerFactory.getLogger(InMemoryByteStore.class);

    private final int bufferSize;

    private List<byte[]> buffers = new ArrayList<>();
    private int length = 0;

    InMemoryByteStore(int bufferSize) {
        this.bufferSize = bufferSize;
    }

    public synchronized int getBufferCount() {
        return buffers.size();
    }

    @Override
    public synchronized int read(ByteBuffer buffer, long offset, int count) throws IOException {

        logger.debug("read, offset {}, count {}, current length {}", offset, count, length);

        if (buffers == null)
            throw new IOException("read on a closed ByteStore");

        if (offset >= length)
            return 0;

        count = Math.min(count, length - (int) offset);

        int read = 0;

        while (read < count) {

            int n = ((int) offset + read) / bufferSize;
            assert n < buffers.size();

            byte[] src = buffers.get(n);

            int srcOffset = (int) offset + read - bufferSize * n;
            int bytesToRead = Math.min(count - read, bufferSize - srcOffset);

            buffer.put(src, srcOffset, bytesToRead);
            read += bytesToRead;
        }

        return read;
    }

    @Override
    public synchronized int write(ByteBuffer buffer, long offset, int count) throws IOException {

        logger.debug("write, offset {}, count {}, current length {}", offset, count, length);

        if (buffers == null)
            throw new IOException("write on a closed ByteStore");

        assert offset <= length;

        int written = 0;

        while (written < count) {

            int n = ((int) offset + written) / bufferSize;

            assert n <= buffers.size();

            byte[] dst;
            if (n < buffers.size())
                dst = buffers.get(n);
            else {
                dst = new byte[bufferSize];
                buffers.add(dst);
            }

            int dstOffset = (int) offset + written - bufferSize * n;
            int bytesToWrite = Math.min(count - written, bufferSize - dstOffset);

            buffer.get(dst, dstOffset, bytesToWrite);
            written += bytesToWrite;
        }

        length = Math.max(length, (int) offset + written);

        return written;
    }

    @Override
    public synchronized void truncate(long length) throws IOException {

        logger.debug("truncate, length {}, current length {}", length, this.length);

        if (buffers == null)
            throw new IOException("truncate on a closed ByteStore");

        if (length == this.length)
            return;

        if (length < this.length) {
            this.length = (int) length;
        } else {
            ByteBuffer buffer = ByteBuffer.allocate(bufferSize);
            while (this.length < length) {
                buffer.rewind();
                write(buffer, this.length, (int) Math.min(bufferSize, length - this.length));
            }
        }
    }

    @Override
    public synchronized void close() throws IOException {

        logger.debug("close");

        if (buffers == null)
            return;

        buffers.clear();
        buffers = null;
    }
}

class InMemoryByteStoreFactory {

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