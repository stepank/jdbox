package jdbox.localstate.interfaces;

import jdbox.localstate.knownfiles.KnownFiles;

public interface LocalUpdateSafe {
    void run(KnownFiles knownFiles);
}
