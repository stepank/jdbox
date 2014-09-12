package jdbox;

import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.FileList;
import com.google.common.base.Function;
import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.List;

public class DriveAdapter {

    private static final Logger logger = LoggerFactory.getLogger(DriveAdapter.class);

    private final Drive drive;

    public DriveAdapter(Drive drive) {
        this.drive = drive;
    }

    public List<File> getChildren(String id) throws Exception {
        return getChildren(id, null);
    }

    public List<File> getChildren(String id, String where) throws Exception {

        logger.debug("get children of {}, constrained by {}", id, where);

        try {

            String q = "'" + id + "' in parents and trashed = false";
            if (where != null && !where.equals(""))
                q += " and " + where;

            return Lists.transform(
                    drive.files().list().setQ(q).execute().getItems(),
                    new Function<com.google.api.services.drive.model.File, File>() {
                        @Nullable
                        @Override
                        public File apply(@Nullable com.google.api.services.drive.model.File file) {
                            return new File(file);
                        }
                    });

        } catch (IOException e) {
            throw new Exception("count not retrieve a list of files", e);
        }
    }

    class Exception extends java.lang.Exception {
        Exception(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
