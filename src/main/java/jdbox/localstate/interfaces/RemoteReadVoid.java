package jdbox.localstate.interfaces;

import jdbox.uploader.Uploader;

import java.io.IOException;

public interface RemoteReadVoid {
    void run(Uploader uploader) throws IOException;
}
