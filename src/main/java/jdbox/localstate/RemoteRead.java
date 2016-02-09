package jdbox.localstate;

import java.io.IOException;

public interface RemoteRead<T> {
    T run() throws IOException;
}
