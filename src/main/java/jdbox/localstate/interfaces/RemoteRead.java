package jdbox.localstate.interfaces;

import java.io.IOException;

public interface RemoteRead<T> {
    T run() throws IOException;
}
