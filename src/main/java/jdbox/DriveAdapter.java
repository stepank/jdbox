package jdbox;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponse;
import com.google.api.services.drive.Drive;
import com.google.common.base.Function;
import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
            throw new Exception("could not retrieve a list of files", e);
        }
    }

    public InputStream downloadFileRange(File file, long offset, long count) throws Exception {

        logger.debug("downloading file {}, offset is {}, count is {}", file, offset, count);

        try {
            HttpRequest request = drive.getRequestFactory().buildGetRequest(new GenericUrl(file.getDownloadUrl()));
            request.getHeaders().setRange(String.format("bytes=%s-%s", offset, offset + count - 1));
            HttpResponse response = request.execute();
            return response.getContent();
        } catch (IOException e) {
            throw new Exception("could not download file", e);
        }
    }

    class Exception extends java.lang.Exception {
        Exception(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
