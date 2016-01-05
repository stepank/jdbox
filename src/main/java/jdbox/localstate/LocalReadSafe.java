package jdbox.localstate;

import jdbox.localstate.knownfiles.KnownFiles;

public interface LocalReadSafe<T> {
    T run(KnownFiles knownFiles);
}
