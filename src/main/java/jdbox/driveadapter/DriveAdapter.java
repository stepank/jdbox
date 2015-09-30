package jdbox.driveadapter;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.InputStreamContent;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.About;
import com.google.api.services.drive.model.ChangeList;
import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.SettableFuture;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;

public class DriveAdapter {

    private static final Logger logger = LoggerFactory.getLogger(DriveAdapter.class);

    private final Drive drive;

    @Inject
    public DriveAdapter(Drive drive) {
        this.drive = drive;
    }

    public BasicInfo getBasicInfo() throws IOException {

        logger.debug("getting basic info");

        return new BasicInfo(drive.about().get().execute());
    }

    public Changes getChanges(long startChangeId) throws IOException {

        logger.debug("getting changes starting with {}", startChangeId);

        return new Changes(drive.changes().list().setStartChangeId(startChangeId).execute());
    }

    public List<File> getChildren(File file) throws IOException {

        logger.debug("getting children of {}", file);

        return Lists.transform(
                drive.files().list()
                        .setQ("'" + file.getId() + "' in parents and trashed = false")
                        .setFields("items(" + File.fields + ")").setMaxResults(1000).execute().getItems(),
                new Function<com.google.api.services.drive.model.File, File>() {
                    @Override
                    public File apply(com.google.api.services.drive.model.File file) {
                        return new File(file);
                    }
                });
    }

    public File createFolder(String name, File parent) throws IOException {
        File file = new File();
        file.setName(name);
        file.setIsDirectory(true);
        if (parent != null)
            file.setParentId(parent.getId());
        return createFile(file, null);
    }

    public File createFile(String name, File parent, InputStream content) throws IOException {
        File file = new File();
        file.setName(name);
        file.setParentId(parent.getId());
        return createFile(file, content);
    }

    public File createFile(File file, InputStream content) throws IOException {

        logger.debug("creating {}", file);

        com.google.api.services.drive.model.File gdFile = file.toGdFile();

        Drive.Files.Insert request;
        if (file.isDirectory()) {
            request = drive.files().insert(gdFile);
        } else {
            request = drive.files().insert(gdFile, new InputStreamContent(file.getMimeType(), content));
            request.getMediaHttpUploader().setDirectUploadEnabled(true);
        }

        return new File(request.execute());
    }

    public void deleteFile(File file) throws IOException {

        if (file.getEtag() == null)
            throw new AssertionError("file.etag must not be null");

        logger.debug("deleting {}", file);

        Drive.Files.Delete request = drive.files().delete(file.getId());

        request.getRequestHeaders().setIfMatch(file.getEtag());

        request.execute();
    }

    public File trashFile(File file) throws IOException {

        if (file.getEtag() == null)
            throw new AssertionError("file.etag must not be null");

        logger.debug("trashing {}", file);

        Drive.Files.Trash request = drive.files().trash(file.getId());

        request.getRequestHeaders().setIfMatch(file.getEtag());

        return new File(request.execute());
    }

    public File updateFile(File file) throws IOException {

        if (file.getEtag() == null)
            throw new AssertionError("file.etag must not be null");

        logger.debug("updating {}", file);

        com.google.api.services.drive.model.File gdFile = file.toGdFile();

        Drive.Files.Patch request = drive.files().patch(file.getId(), gdFile).setFields(File.fields);

        if (file.getModifiedDate() != null)
            request.setSetModifiedDate(true);

        request.getRequestHeaders().setIfMatch(file.getEtag());

        return new File(request.execute());
    }

    public InputStream downloadFileRange(File file, long offset, long length) throws IOException {

        logger.debug("downloading {}, offset {}, length {}", file, offset, length);

        if (file.getDownloadUrl() == null)
            throw new AssertionError("file.downloadUrl must not be null");

        HttpRequest request = drive.getRequestFactory().buildGetRequest(new GenericUrl(file.getDownloadUrl()));
        request.getHeaders().setRange(String.format("bytes=%s-%s", offset, offset + length - 1));
        HttpResponse response = request.execute();
        return response.getContent();
    }

    public Future<InputStream> downloadFileRangeAsync(
            final File file, final long offset, final long length, Executor executor) {

        logger.debug("requesting a stream of {}, offset {}, length {}", file, offset, length);

        final SettableFuture<InputStream> future = SettableFuture.create();

        final Date start = new Date();
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    InputStream stream = DriveAdapter.this.downloadFileRange(file, offset, length);
                    logger.debug(
                            "got a stream of {}, offset {}, length {}, exec time {} ms",
                            file, offset, length, new Date().getTime() - start.getTime());
                    future.set(stream);
                } catch (IOException e) {
                    future.setException(e);
                }
            }
        });

        return future;
    }

    public File updateFileContent(File file, InputStream content) throws IOException {

        if (file.getEtag() == null)
            throw new AssertionError("file.etag must not be null");

        logger.debug("updating {}", file);

        Drive.Files.Update request = drive.files().update(
                file.getId(), new com.google.api.services.drive.model.File(), new InputStreamContent(null, content));
        request.getMediaHttpUploader().setDirectUploadEnabled(true);
        request.getRequestHeaders().setIfMatch(file.getEtag());

        return new File(request.execute());
    }

    public class BasicInfo {

        public final long largestChangeId;
        public final String rootFolderId;

        public BasicInfo(About about) {
            largestChangeId = about.getLargestChangeId();
            rootFolderId = about.getRootFolderId();
        }
    }

    public class Changes {

        public final long largestChangeId;
        public final List<Change> items;

        public Changes(ChangeList changes) {
            largestChangeId = changes.getLargestChangeId();
            items = Lists.transform(changes.getItems(), new Function<com.google.api.services.drive.model.Change, Change>() {
                @Override
                public Change apply(com.google.api.services.drive.model.Change change) {
                    return new Change(change);
                }
            });
        }
    }

    public class Change {

        public final String fileId;
        public final File file;
        public final boolean isDeleted;

        private Change(com.google.api.services.drive.model.Change change) {
            fileId = change.getFileId();
            file = change.getFile() != null ? new File(change.getFile()) : null;
            isDeleted = change.getDeleted();
        }
    }
}
