package jdbox.uploader;

import jdbox.models.fileids.FileId;

abstract public class BaseTask implements Task {

    private final String label;
    private final FileId fileId;
    private final String etag;
    private final FileId dependsOn;
    private final boolean blocksDependentTasks;

    public BaseTask(String label, FileId fileId, String etag, FileId dependsOn, boolean blocksDependentTasks) {
        this.label = label;
        this.fileId = fileId;
        this.etag = etag;
        this.dependsOn = dependsOn;
        this.blocksDependentTasks = blocksDependentTasks;
    }

    @Override
    public String getLabel() {
        return label;
    }

    @Override
    public FileId getFileId() {
        return fileId;
    }

    @Override
    public String getEtag() {
        return etag;
    }

    @Override
    public FileId getDependsOn() {
        return dependsOn;
    }

    @Override
    public boolean blocksDependentTasks() {
        return blocksDependentTasks;
    }
}
