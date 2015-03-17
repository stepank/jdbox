package jdbox;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.InputStreamContent;
import com.google.api.client.util.DateTime;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.About;
import com.google.api.services.drive.model.ChangeList;
import com.google.api.services.drive.model.ParentReference;
import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import jdbox.filetree.File;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class DriveAdapter {

    private static final Logger logger = LoggerFactory.getLogger(DriveAdapter.class);

    private final Drive drive;

    @Inject
    public DriveAdapter(Drive drive) {
        this.drive = drive;
    }

    public List<File> getChildren(File file) throws DriveException {

        logger.debug("getting children of {}", file);

        try {
            return Lists.transform(
                    drive.files().list()
                            .setQ("'" + file.getId() + "' in parents and trashed = false")
                            .setFields(File.fields).setMaxResults(1000).execute().getItems(),
                    new Function<com.google.api.services.drive.model.File, File>() {
                        @Nullable
                        @Override
                        public File apply(@Nullable com.google.api.services.drive.model.File file) {
                            return new File(file);
                        }
                    });
        } catch (IOException e) {
            throw new DriveException("could not retrieve a list of files", e);
        }
    }

    public BasicInfo getBasicInfo() throws DriveException {

        logger.debug("getting basic info");

        try {
            return new BasicInfo(drive.about().get().execute());
        } catch (IOException e) {
            throw new DriveException("could not retrieve basic info", e);
        }
    }

    public Changes getChanges(long startChangeId) throws DriveException {

        logger.debug("getting changes starting with {}", startChangeId);

        try {
            return new Changes(drive.changes().list().setStartChangeId(startChangeId).execute());
        } catch (IOException e) {
            throw new DriveException("could not retrieve a list of changes", e);
        }
    }

    public InputStream downloadFileRange(File file, long offset, long count) throws DriveException {

        logger.debug("downloading {}, offset {}, count {}", file, offset, count);

        try {
            HttpRequest request = drive.getRequestFactory().buildGetRequest(new GenericUrl(file.getDownloadUrl()));
            request.getHeaders().setRange(String.format("bytes=%s-%s", offset, offset + count - 1));
            HttpResponse response = request.execute();
            return response.getContent();
        } catch (IOException e) {
            throw new DriveException("could not download file", e);
        }
    }

    public File createFile(String name, File parent, InputStream content) throws DriveException {
        return createFile(new File(null, name, parent.getId(), false), content);
    }

    public File createFile(File file, InputStream content) throws DriveException {

        logger.debug("creating {}", file);

        com.google.api.services.drive.model.File gdFile =
                new com.google.api.services.drive.model.File()
                        .setTitle(file.getName());

        if (file.getParentIds() != null && file.getParentIds().size() > 0)
            gdFile.setParents(Collections.singletonList(new ParentReference().setId(file.getParentIds().get(0))));

        if (file.getCreatedDate() != null)
            gdFile.setCreatedDate(new DateTime(file.getCreatedDate()));

        if (file.isDirectory())
            gdFile.setMimeType("application/vnd.google-apps.folder");

        try {
            Drive.Files.Insert request;
            if (file.isDirectory()) {
                request = drive.files().insert(gdFile);
            } else {
                request = drive.files().insert(gdFile, new InputStreamContent("text/plain", content));
                request.getMediaHttpUploader().setDirectUploadEnabled(true);
            }
            return new File(request.execute());
        } catch (IOException e) {
            throw new DriveException("could not create file", e);
        }
    }

    public File createFolder(String name) throws DriveException {
        return createFile(new File(null, name, null, true), null);
    }

    public File createFolder(String name, File parent) throws DriveException {
        return createFile(new File(null, name, parent.getId(), true), null);
    }

    public void deleteFile(File file) throws DriveException {

        logger.debug("deleting {}", file);

        try {
            drive.files().delete(file.getId()).execute();
        } catch (IOException e) {
            throw new DriveException("could not delete file", e);
        }
    }

    public void trashFile(File file) throws DriveException {

        logger.debug("trashing {}", file);

        try {
            drive.files().trash(file.getId()).execute();
        } catch (IOException e) {
            throw new DriveException("could not trash file", e);
        }
    }

    public void renameFile(File file, String newName) throws DriveException {

        logger.debug("renaming {} to {}", file, newName);

        com.google.api.services.drive.model.File newFile = new com.google.api.services.drive.model.File().setTitle(newName);

        try {
            drive.files().patch(file.getId(), newFile).setFields("title").execute();
        } catch (IOException e) {
            throw new DriveException("could not rename file", e);
        }
    }

    public void touchFile(File file, Date accessedDate) throws DriveException {
        touchFile(file, accessedDate, null);
    }

    public void touchFile(File file, Date accessedDate, Date modifiedDate) throws DriveException {

        logger.debug("touching {}", file);

        com.google.api.services.drive.model.File newFile =
                new com.google.api.services.drive.model.File().setLastViewedByMeDate(new DateTime(accessedDate));

        String fields = "lastViewedByMeDate";

        if (modifiedDate != null) {
            newFile.setModifiedByMeDate(new DateTime(modifiedDate));
            newFile.setModifiedDate(new DateTime(modifiedDate));
            fields += ",modifiedDate,modifiedByMeDate";
        }

        try {
            Drive.Files.Patch request = drive.files().patch(file.getId(), newFile).setFields(fields);
            if (modifiedDate != null)
                request.setSetModifiedDate(true);
            request.execute();
        } catch (IOException e) {
            throw new DriveException("could not touch file", e);
        }
    }

    public File updateFileContent(File file, InputStream content) throws DriveException {

        logger.debug("updating {}", file);

        try {
            Drive.Files.Update request = drive.files().update(
                    file.getId(), new com.google.api.services.drive.model.File(), new InputStreamContent("text/plain", content));
            request.getMediaHttpUploader().setDirectUploadEnabled(true);
            return new File(request.execute());
        } catch (IOException e) {
            throw new DriveException("could not update file", e);
        }
    }

    public void moveFile(File file, File parent) throws DriveException {
        logger.debug("moving {} to {}", file, parent);
        updateParentIds(file, Collections.singletonList(parent.getId()));
    }

    public void updateParentIds(File file, List<String> parentIds) throws DriveException {

        logger.debug("updating parent ids of {}", file);

        com.google.api.services.drive.model.File newFile = new com.google.api.services.drive.model.File()
                .setParents(Lists.transform(parentIds, new Function<String, ParentReference>() {
                    @Nullable
                    @Override
                    public ParentReference apply(@Nullable String parentId) {
                        return new ParentReference().setId(parentId);
                    }
                }));

        try {
            drive.files().patch(file.getId(), newFile).setFields("parents").execute();
        } catch (IOException e) {
            throw new DriveException("could not update a file's parent ids", e);
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

    public class BasicInfo {

        public final long largestChangeId;
        public final String rootFolderId;

        public BasicInfo(About about) {
            largestChangeId = about.getLargestChangeId();
            rootFolderId = about.getRootFolderId();
        }
    }

    public class DriveException extends Exception {
        private DriveException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
