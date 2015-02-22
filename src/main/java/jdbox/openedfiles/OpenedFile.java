package jdbox.openedfiles;

import com.google.common.util.concurrent.ListenableFuture;
import jdbox.filetree.File;

import java.io.InputStream;
import java.nio.ByteBuffer;

public interface OpenedFile {

    public File getOrigin();

    public int read(ByteBuffer buffer, long offset, int count) throws Exception;

    public int write(ByteBuffer buffer, long offset, int count) throws Exception;

    void close() throws Exception;
}
