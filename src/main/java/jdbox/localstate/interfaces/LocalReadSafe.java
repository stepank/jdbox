package jdbox.localstate.interfaces;

import jdbox.localstate.knownfiles.KnownFiles;

public interface LocalReadSafe<T> {
    T run(KnownFiles knownFiles);
}
