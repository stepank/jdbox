package jdbox.uploader;

import jdbox.models.fileids.FileId;

public abstract class Task {

    private final String label;
    private final FileId fileId;
    private final FileId dependsOn;
    private final boolean blocksDependentTasks;

    public Task(String label, FileId fileId) {
        this(label, fileId, null);
    }

    public Task(String label, FileId fileId, FileId dependsOn) {
        this(label, fileId, dependsOn, false);
    }

    public Task(String label, FileId fileId, FileId dependsOn, boolean blocksDependentTasks) {
        this.label = label;
        this.fileId = fileId;
        this.dependsOn = dependsOn;
        this.blocksDependentTasks = blocksDependentTasks;
    }

    public abstract void run() throws Exception;

    public FileId getFileId() {
        return fileId;
    }

    public FileId getDependsOn() {
        return dependsOn;
    }

    public boolean blocksDependentTasks() {
        return blocksDependentTasks;
    }

    @Override
    public String toString() {
        return "BaseTask{" +
                "label='" + label + '\'' +
                ", fileId=" + fileId +
                ", dependsOn=" + dependsOn +
                ", blocksDependentTasks=" + blocksDependentTasks +
                '}';
    }
}
