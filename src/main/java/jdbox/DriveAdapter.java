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
import com.google.common.util.concurrent.SettableFuture;
import com.google.inject.Inject;
import jdbox.filetree.File;
import jdbox.filetree.FileId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public class DriveAdapter {

    public enum Field {
        NAME,
        PARENT_IDS,
        ACCESSED_DATE,
        MODIFIED_DATE
    }

    private static final Logger logger = LoggerFactory.getLogger(DriveAdapter.class);

    private final Drive drive;
    private final ExecutorService executor;

    @Inject
    public DriveAdapter(Drive drive, ExecutorService executor) {
        this.drive = drive;
        this.executor = executor;
    }

    public List<File> getChildren(File file) throws IOException {

        logger.debug("getting children of {}", file);

        return Lists.transform(
                drive.files().list()
                        .setQ("'" + file.getId().get() + "' in parents and trashed = false")
                        .setFields(File.fields).setMaxResults(1000).execute().getItems(),
                new Function<com.google.api.services.drive.model.File, File>() {
                    @Nullable
                    @Override
                    public File apply(@Nullable com.google.api.services.drive.model.File file) {
                        return new File(file);
                    }
                });
    }

    public BasicInfo getBasicInfo() throws IOException {

        logger.debug("getting basic info");

        return new BasicInfo(drive.about().get().execute());
    }

    public Changes getChanges(long startChangeId) throws IOException {

        logger.debug("getting changes starting with {}", startChangeId);

        return new Changes(drive.changes().list().setStartChangeId(startChangeId).execute());
    }

    public InputStream downloadFileRange(File file, long offset, long length) throws IOException {

        if (!file.isReal() || !file.getId().isSet())
            throw new IllegalArgumentException("request for a stream of a file that has not been uploaded yet");

        logger.debug("downloading {}, offset {}, length {}", file, offset, length);

        HttpRequest request = drive.getRequestFactory().buildGetRequest(new GenericUrl(file.getDownloadUrl()));
        request.getHeaders().setRange(String.format("bytes=%s-%s", offset, offset + length - 1));
        HttpResponse response = request.execute();
        return response.getContent();
    }

    public Future<InputStream> downloadFileRangeAsync(final File file, final long offset, final long length) {

        if (!file.isReal() || !file.getId().isSet())
            throw new IllegalArgumentException("request for a stream of a file that has not been uploaded yet");

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

    public File createFile(String name, File parent, InputStream content) throws IOException {
        return createFile(new File(name, parent, false), content);
    }

    public File createFolder(String name) throws IOException {
        return createFile(new File(name, null, true), null);
    }

    public File createFolder(String name, File parent) throws IOException {
        return createFile(new File(name, parent, true), null);
    }

    public File createFile(File file, InputStream content) throws IOException {

        logger.debug("creating {}", file);

        com.google.api.services.drive.model.File gdFile =
                new com.google.api.services.drive.model.File()
                        .setTitle(file.getName());

        if (file.getParentIds() != null && file.getParentIds().size() > 0)
            gdFile.setParents(Lists.newLinkedList(
                    Collections2.transform(file.getParentIds(), new Function<FileId, ParentReference>() {
                        @Override
                        public ParentReference apply(FileId parentId) {
                            if (!parentId.isSet())
                                throw new UnsupportedOperationException("parent id is null");
                            return new ParentReference().setId(parentId.get());
                        }
                    })));

        if (file.getCreatedDate() != null)
            gdFile.setCreatedDate(new DateTime(file.getCreatedDate()));

        if (file.isDirectory())
            gdFile.setMimeType("application/vnd.google-apps.folder");

        Drive.Files.Insert request;
        if (file.isDirectory()) {
            request = drive.files().insert(gdFile);
        } else {
            request = drive.files().insert(gdFile, new InputStreamContent(null, content));
            request.getMediaHttpUploader().setDirectUploadEnabled(true);
        }

        return new File(request.execute());
    }

    public void deleteFile(File file) throws IOException {

        logger.debug("deleting {}", file);

        drive.files().delete(file.getId().get()).execute();
    }

    public void trashFile(File file) throws IOException {

        logger.debug("trashing {}", file);

        drive.files().trash(file.getId().get()).execute();
    }

    public void updateFile(File file, Field update) throws IOException {
        updateFile(file, EnumSet.of(update));
    }

    public void updateFile(File file, EnumSet<Field> update) throws IOException {

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
                    Collections2.transform(file.getParentIds(), new Function<FileId, ParentReference>() {
                        @Override
                        public ParentReference apply(FileId parentId) {
                            if (!parentId.isSet())
                                throw new UnsupportedOperationException("file id is null");
                            return new ParentReference().setId(parentId.get());
                        }
                    })));
        }

        Drive.Files.Patch request =
                drive.files().patch(file.getId().get(), newFile).setFields(Joiner.on(",").join(fields));
        if (update.contains(Field.MODIFIED_DATE))
            request.setSetModifiedDate(true);
        request.execute();
    }

    public void renameFile(File file) throws IOException {
        updateFile(file, Field.NAME);
    }

    public void renameFile(File file, String newName) throws IOException {
        renameFile(file.setName(newName));
    }

    public void touchFile(File file, boolean setModifiedDate) throws IOException {
        if (!setModifiedDate)
            updateFile(file, Field.ACCESSED_DATE);
        else
            updateFile(file, EnumSet.of(Field.ACCESSED_DATE, Field.MODIFIED_DATE));
    }

    public void moveFile(File file, File parent) throws IOException {
        file.getParentIds().clear();
        file.getParentIds().add(parent.getId());
        updateParentIds(file);
    }

    public void updateParentIds(File file) throws IOException {
        updateFile(file, Field.PARENT_IDS);
    }

    public File updateFileContent(File file, InputStream content) throws IOException {

        logger.debug("updating {}", file);

        Drive.Files.Update request = drive.files().update(
                file.getId().get(), new com.google.api.services.drive.model.File(),
                new InputStreamContent(null, content));
        request.getMediaHttpUploader().setDirectUploadEnabled(true);
        return new File(request.execute());
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
}
