package jdbox.uploader;

import com.google.api.client.http.HttpResponseException;
import jdbox.OperationContext;
import jdbox.datapersist.ChangeSet;
import jdbox.driveadapter.DriveAdapter;
import jdbox.driveadapter.Field;
import jdbox.models.File;
import jdbox.models.fileids.FileId;
import jdbox.models.fileids.FileIdStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.io.IOException;
import java.util.EnumSet;

public abstract class DriveTask implements Task {

    private static final Logger logger = LoggerFactory.getLogger(DriveTask.class);

    private final FileIdStore fileIdStore;
    private final DriveAdapter drive;
    private final String label;
    private final File original;
    private final File modified;
    private final EnumSet<Field> fields;
    private final FileId dependsOn;
    private final boolean blocksDependentTasks;

    public DriveTask(
            FileIdStore fileIdStore, DriveAdapter drive, String label,
            File original, File modified, EnumSet<Field> fields) {
        this(fileIdStore, drive, label, original, modified, fields, null);
    }

    public DriveTask(
            FileIdStore fileIdStore, DriveAdapter drive, String label,
            File original, File modified, EnumSet<Field> fields, FileId dependsOn) {
        this(fileIdStore, drive, label, original, modified, fields, dependsOn, false);
    }

    public DriveTask(
            FileIdStore fileIdStore, DriveAdapter drive, String label,
            File original, File modified, EnumSet<Field> fields, FileId dependsOn, boolean blocksDependentTasks) {
        this.fileIdStore = fileIdStore;
        this.drive = drive;
        this.label = OperationContext.get().path + ": " + label;
        this.original = original;
        this.modified = modified;
        this.fields = fields;
        this.dependsOn = dependsOn;
        this.blocksDependentTasks = blocksDependentTasks;
    }

    public abstract jdbox.driveadapter.File run(ChangeSet changeSet, jdbox.driveadapter.File file) throws IOException;

    @Override
    public String getLabel() {
        return label;
    }

    @Override
    public FileId getFileId() {
        return modified.getId();
    }

    @Override
    public String getEtag() {
        return modified.getEtag();
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
    public String run(ChangeSet changeSet, String etag) throws ConflictException, IOException {

        jdbox.driveadapter.File file = modified.toDaFile(fields);

        int attempt = 0;

        while (true) {

            attempt++;

            file.setEtag(etag);

            try {

                return run(changeSet, file).getEtag();

            } catch (IOException e) {

                logger.warn("an error occured while executing {}", this, e);

                if (e instanceof HttpResponseException && ((HttpResponseException) e).getStatusCode() == 412) {

                    File cloudFile = new File(fileIdStore, drive.getFile(file));

                    if (attempt < 3 && filesAreEqual(original, cloudFile))
                        etag = cloudFile.getEtag();
                    else
                        throw new ConflictException(e);
                }
            }
        }
    }

    @Override
    public String toString() {
        return "DriveTask{" +
                "label='" + label + '\'' +
                ", file=" + modified +
                ", dependsOn=" + dependsOn +
                ", blocksDependentTasks=" + blocksDependentTasks +
                '}';
    }

    @Override
    public String serialize() {
        throw new NotImplementedException();
    }

    private static boolean filesAreEqual(File a, File b) {
        if (a.getName() != null ? !a.getName().equals(b.getName()) : b.getName() != null)
            return false;
        if (a.getParentIds() != null ? !a.getParentIds().equals(b.getParentIds()) : b.getParentIds() != null)
            return false;
        //noinspection SimplifiableIfStatement
        return a.getMd5Sum() != null ? a.getMd5Sum().equals(b.getMd5Sum()) : b.getMd5Sum() == null;
    }
}
