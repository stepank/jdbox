package jdbox.localstate;

import jdbox.localstate.knownfiles.KnownFiles;
import jdbox.uploader.Uploader;

import java.io.IOException;

public interface LocalUpdate<T> {
    T run(KnownFiles knownFiles, Uploader uploader) throws IOException;
}
