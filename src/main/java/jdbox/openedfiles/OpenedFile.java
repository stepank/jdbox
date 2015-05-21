package jdbox.openedfiles;

import jdbox.models.File;

import java.io.IOException;

public abstract class OpenedFile implements ByteStore {

    public abstract File release() throws IOException;

    public void close() throws IOException {
        release();
    }
}
