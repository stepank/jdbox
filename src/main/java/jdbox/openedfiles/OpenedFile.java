package jdbox.openedfiles;

import java.nio.ByteBuffer;

public interface OpenedFile {

    public int read(ByteBuffer buffer, long offset, int count) throws Exception;

    public int write(ByteBuffer buffer, long offset, int count) throws Exception;

    public void truncate(long offset) throws Exception;
}
