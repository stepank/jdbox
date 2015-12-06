package jdbox.content.bytestores;

import java.io.IOException;
import java.nio.ByteBuffer;

public interface ByteStore extends ByteSource {

    int write(ByteBuffer buffer, long offset, int count) throws IOException;

    void truncate(long offset) throws IOException;

    long getSize();
}
