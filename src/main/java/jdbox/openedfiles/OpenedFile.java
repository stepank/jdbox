package jdbox.openedfiles;

import jdbox.filetree.File;

import java.nio.ByteBuffer;

public interface OpenedFile {

    public File getOrigin();

    public int read(ByteBuffer buffer, long offset, int count) throws Exception;

    public int write(ByteBuffer buffer, long offset, int count) throws Exception;

    void close() throws Exception;
}
