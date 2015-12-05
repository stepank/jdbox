package jdbox.content.bytestores;

import java.io.IOException;
import java.nio.ByteBuffer;

public interface ByteSource extends AutoCloseable {

    int read(ByteBuffer buffer, long offset, int count) throws IOException;

    void close() throws IOException;
}
