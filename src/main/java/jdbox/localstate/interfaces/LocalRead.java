package jdbox.localstate.interfaces;

import jdbox.localstate.knownfiles.KnownFiles;

import java.io.IOException;

public interface LocalRead<T> {
    T run(KnownFiles knownFiles) throws IOException;
}
