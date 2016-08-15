package jdbox.localstate.interfaces;

import jdbox.datapersist.ChangeSet;
import jdbox.localstate.knownfiles.KnownFiles;
import jdbox.uploader.Uploader;

public interface LocalUpdateSafe {
    void run(ChangeSet changeSet, KnownFiles knownFiles, Uploader uploader);
}
