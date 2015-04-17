package jdbox.openedfiles;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

public class ByteSources {

    public static void copy(ByteSource source, ByteStore destination) throws IOException {

        final int bufferSize = 16 * 1024;

        ByteBuffer buffer = ByteBuffer.allocate(bufferSize);

        int offset = 0;
        int read;

        do {
            read = source.read(buffer, offset, bufferSize);
            buffer.rewind();
            destination.write(buffer, offset, read);
            offset += read;
        } while (read == bufferSize);
    }

    public static ByteSourceInputStream toInputStream(ByteSource source) {
        return new ByteSourceInputStream(source);
    }

    private static class ByteSourceInputStream extends InputStream {

        private final ByteBuffer oneByteBuffer = ByteBuffer.allocate(1);

        private ByteSource source;
        private int position = 0;

        public ByteSourceInputStream(ByteSource source) {
            this.source = source;
        }

        @Override
        public synchronized int read() throws IOException {
            if (source == null)
                throw new IOException("read on a closed InputStream");
            int read = source.read(oneByteBuffer, position, 1);
            oneByteBuffer.reset();
            position++;
            return read == 0 ? -1 : oneByteBuffer.get();
        }

        @Override
        public synchronized int read(byte[] bytes, int offset, int count) throws IOException {
            if (source == null)
                throw new IOException("read on a closed InputStream");
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            buffer.position(offset);
            int read = source.read(buffer, position, count);
            position += read;
            return read == 0 ? -1 : read;
        }

        @Override
        public synchronized void close() throws IOException {
            source = null;
        }
    }
}