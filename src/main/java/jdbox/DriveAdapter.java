package jdbox;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.InputStreamContent;
import com.google.api.client.repackaged.com.google.common.base.Joiner;
import com.google.api.client.util.DateTime;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.About;
import com.google.api.services.drive.model.ChangeList;
import com.google.api.services.drive.model.ParentReference;
import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import jdbox.filetree.File;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;

public class DriveAdapter {

    public enum Field {
        NAME,
        PARENT_IDS,
        ACCESSED_DATE,
        MODIFIED_DATE
    }

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
            gdFile.setParents(Lists.newLinkedList(
                    Collections2.transform(file.getParentIds(), new Function<String, ParentReference>() {
                        @Nullable
                        @Override
                        public ParentReference apply(@Nullable String parentId) {
                            return new ParentReference().setId(parentId);
                        }
                    })));

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

    public void updateFile(File file, Field update) throws DriveException {
        updateFile(file, EnumSet.of(update));
    }

    public void updateFile(File file, EnumSet<Field> update) throws DriveException {

        logger.debug("updating {}, fields {}", file, update);

        com.google.api.services.drive.model.File newFile = new com.google.api.services.drive.model.File();
        List<String> fields = new LinkedList<>();

        if (update.contains(Field.NAME)) {
            fields.add("title");
            newFile.setTitle(file.getName());
        }

        if (update.contains(Field.ACCESSED_DATE)) {
            fields.add("lastViewedByMeDate");
            newFile.setLastViewedByMeDate(new DateTime(file.getAccessedDate()));
        }

        if (update.contains(Field.MODIFIED_DATE)) {
            fields.add("modifiedDate,modifiedByMeDate");
            newFile.setModifiedDate(new DateTime(file.getModifiedDate()));
            newFile.setModifiedByMeDate(new DateTime(file.getModifiedDate()));
        }

        if (update.contains(Field.PARENT_IDS)) {
            fields.add("parents");
            newFile.setParents(Lists.newLinkedList(
                    Collections2.transform(file.getParentIds(), new Function<String, ParentReference>() {
                        @Nullable
                        @Override
                        public ParentReference apply(@Nullable String parentId) {
                            return new ParentReference().setId(parentId);
                        }
                    })));
        }

        try {
            Drive.Files.Patch request =
                    drive.files().patch(file.getId(), newFile).setFields(Joiner.on(",").join(fields));
            if (update.contains(Field.MODIFIED_DATE))
                request.setSetModifiedDate(true);
            request.execute();
        } catch (IOException e) {
            throw new DriveException("could not update file", e);
        }
    }

    public void renameFile(File file) throws DriveException {
        updateFile(file, Field.NAME);
    }

    public void renameFile(File file, String newName) throws DriveException {
        renameFile(file.setName(newName));
    }

    public void touchFile(File file) throws DriveException {
        touchFile(file, false);
    }

    public void touchFile(File file, boolean setModifiedDate) throws DriveException {
        if (!setModifiedDate)
            updateFile(file, Field.ACCESSED_DATE);
        else
            updateFile(file, EnumSet.of(Field.ACCESSED_DATE, Field.MODIFIED_DATE));
    }

    public void touchFile(File file, Date accessedDate) throws DriveException {
        touchFile(file.setAccessedDate(accessedDate));
    }

    public void moveFile(File file, File parent) throws DriveException {
        file.getParentIds().clear();
        file.getParentIds().add(parent.getId());
        updateParentIds(file);
    }

    public void updateParentIds(File file) throws DriveException {
        updateFile(file, Field.PARENT_IDS);
    }

    public File updateFileContent(File file, InputStream content) throws DriveException {

        logger.debug("updating {}", file);

        try {
            Drive.Files.Update request = drive.files().update(
                    file.getId(), new com.google.api.services.drive.model.File(),
                    new InputStreamContent("text/plain", content));
            request.getMediaHttpUploader().setDirectUploadEnabled(true);
            return new File(request.execute());
        } catch (IOException e) {
            throw new DriveException("could not update file", e);
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
