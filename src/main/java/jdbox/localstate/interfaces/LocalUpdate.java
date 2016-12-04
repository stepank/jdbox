package jdbox.localstate.interfaces;

import jdbox.localstate.knownfiles.KnownFiles;

import java.io.IOException;

public interface LocalUpdate<T> {
    UpdateResult<T> run(KnownFiles knownFiles) throws IOException;
}
