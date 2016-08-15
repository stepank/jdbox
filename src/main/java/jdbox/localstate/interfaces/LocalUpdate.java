package jdbox.localstate.interfaces;

import jdbox.datapersist.ChangeSet;
import jdbox.localstate.knownfiles.KnownFiles;
import jdbox.datapersist.StorageException;
import jdbox.uploader.Uploader;

import java.io.IOException;

public interface LocalUpdate<T> {
    T run(ChangeSet changeSet, KnownFiles knownFiles, Uploader uploader) throws IOException, StorageException;
}
