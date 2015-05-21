package jdbox.openedfiles;

import jdbox.models.File;

import java.io.IOException;
import java.nio.ByteBuffer;

interface ByteStore extends ByteSource {

    int write(ByteBuffer buffer, long offset, int count) throws IOException;

    void truncate(long offset) throws IOException;
}

interface ByteStoreFactory {
    ByteStore create(File file);
}
