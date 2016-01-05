package jdbox.localstate;

import jdbox.localstate.knownfiles.KnownFiles;
import jdbox.uploader.Uploader;

public interface LocalUpdateSafe<T> {
    T run(KnownFiles knownFiles, Uploader uploader);
}
