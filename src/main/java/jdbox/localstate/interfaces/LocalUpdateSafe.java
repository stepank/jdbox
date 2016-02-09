package jdbox.localstate.interfaces;

import jdbox.localstate.knownfiles.KnownFiles;
import jdbox.uploader.Uploader;

public interface LocalUpdateSafe {
    void run(KnownFiles knownFiles, Uploader uploader);
}
