package jdbox.uploader;

import jdbox.models.File;
import jdbox.models.fileids.FileId;

import java.io.IOException;

public abstract class DriveTask implements Task {

    private final String label;
    private final File file;
    private final FileId dependsOn;
    private final boolean blocksDependentTasks;

    public DriveTask(String label, File file) {
        this(label, file, null);
    }

    public DriveTask(String label, File file, FileId dependsOn) {
        this(label, file, dependsOn, false);
    }

    public DriveTask(String label, File file, FileId dependsOn, boolean blocksDependentTasks) {
        this.label = label;
        this.file = file;
        this.dependsOn = dependsOn;
        this.blocksDependentTasks = blocksDependentTasks;
    }

    public abstract jdbox.driveadapter.File run(jdbox.driveadapter.File file) throws IOException;

    @Override
    public String getLabel() {
        return label;
    }

    @Override
    public FileId getFileId() {
        return file.getId();
    }

    @Override
    public String getEtag() {
        return file.getEtag();
    }

    @Override
    public FileId getDependsOn() {
        return dependsOn;
    }

    @Override
    public boolean blocksDependentTasks() {
        return blocksDependentTasks;
    }

    /**
     * @param etag The current etag of the file.
     * @return The file's etag obtained as a result of the performed operation.
     */
    @Override
    public String run(String etag) throws IOException {
        jdbox.driveadapter.File file = this.file.toDaFile();
        file.setEtag(etag);
        return run(file).getEtag();
    }

    @Override
    public String toString() {
        return "DriveTask{" +
                "label='" + label + '\'' +
                ", file=" + file +
                ", dependsOn=" + dependsOn +
                ", blocksDependentTasks=" + blocksDependentTasks +
                '}';
    }
}