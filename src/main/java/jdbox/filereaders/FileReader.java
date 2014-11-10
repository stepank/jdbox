package jdbox.filereaders;

import java.nio.ByteBuffer;

public interface FileReader {

    public void read(ByteBuffer buffer, long offset, int count) throws Exception;
}
