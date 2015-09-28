package jdbox.uploader;

import jdbox.models.fileids.FileId;

import java.io.IOException;

public interface Task {

    String getLabel();

    FileId getFileId();

    String getEtag();

    FileId getDependsOn();

    boolean blocksDependentTasks();

    /**
     * @param etag The current etag of the file.
     * @return The file's etag obtained as a result of the performed operation.
     */
    String run(String etag) throws IOException;
}
