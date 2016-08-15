package jdbox.localstate.knownfiles;

import jdbox.datapersist.ChangeSet;
import jdbox.datapersist.Storage;
import jdbox.driveadapter.BasicInfo;
import jdbox.driveadapter.BasicInfoProvider;
import jdbox.models.File;
import jdbox.models.fileids.FileId;
import jdbox.models.fileids.FileIdStore;
import jdbox.uploader.Uploader;

import java.io.IOException;
import java.util.*;

/**
 * This class is not thread safe, all synchronization should be done externally.
 */
public class KnownFiles {

    public static final String uploadFailureNotificationFileName = "READ ME - UPLOAD IS BROKEN.txt";

    static final String namespace = "know_files";
    static final String fileEntryKeyPrefix = "file/";

    private static final String largestChangeIdEntryKey = "largestChangeId";
    private static final String rootEntryKey = "root";

    private final FileIdStore fileIdStore;
    private final BasicInfoProvider basicInfoProvider;
    private final Storage storage;

    private KnownFile root;
    private final Map<FileId, KnownFile> entries = new HashMap<>();
    private long largestChangeId = 0;
    private KnownFile uploadFailureNotificationFile;

    public KnownFiles(FileIdStore fileIdStore, BasicInfoProvider basicInfoProvider, Storage storage) {
        this.fileIdStore = fileIdStore;
        this.basicInfoProvider = basicInfoProvider;
        this.storage = storage;
    }

    public void init(ChangeSet changeSet) throws IOException {

        List<Map.Entry<String, String>> entries = storage.getData(namespace);

        if (entries.size() == 0) {

            final BasicInfo basicInfo = basicInfoProvider.getBasicInfo();

            setLargestChangeId(changeSet, basicInfo.largestChangeId);
            setRoot(changeSet, fileIdStore.get(basicInfo.rootFolderId));

            return;
        }

        Long largestChangeId = null;
        FileId rootId = null;
        Map<FileId, KnownFileDto> files = new HashMap<>();
        Map<FileId, List<FileId>> children = new HashMap<>();

        for (Map.Entry<String, String> entry : entries) {

            if (entry.getKey().equals(largestChangeIdEntryKey)) {

                largestChangeId = Long.parseLong(entry.getValue());

            } else if (entry.getKey().equals(rootEntryKey)) {

                rootId = fileIdStore.get(entry.getValue());

            } else {

                FileId fileId = FileId.deserialize(entry.getKey().substring(fileEntryKeyPrefix.length()));
                KnownFileDto dto = KnownFileDto.deserialize(entry.getValue());

                files.put(fileId, dto);

                for (FileId parentId : dto.file.getParentIds()) {
                    List<FileId> ch = children.get(parentId);
                    if (ch == null) {
                        ch = new ArrayList<>();
                        children.put(parentId, ch);
                    }
                    ch.add(fileId);
                }
            }
        }

        if (largestChangeId == null)
            throw new IllegalStateException("cannot restore files because largest change id is not known");

        if (rootId == null)
            throw new IllegalStateException("cannot restore files because root file id is not known");

        if (!files.containsKey(rootId))
            throw new IllegalStateException("cannot restore files because root file is not known");

        ChangeSet ignoredChangeSet = new ChangeSet();

        setLargestChangeId(ignoredChangeSet, largestChangeId);

        ArrayList<FileId> queue = new ArrayList<>();
        queue.add(rootId);

        for (int i = 0; i < queue.size(); i++) {

            FileId fileId = queue.get(i);

            KnownFileDto dto = files.get(fileId);

            KnownFile knownFile = this.entries.get(fileId);
            if (knownFile == null) {
                knownFile = create(dto.file);
                if (dto.isTracked)
                    knownFile.setTracked();
            }

            for (FileId parentId : dto.file.getParentIds()) {
                KnownFile parent = get(parentId);
                if (parent != null)
                    parent.tryAddChild(ignoredChangeSet, knownFile);
            }

            List<FileId> ch = children.get(fileId);
            if (ch != null)
                queue.addAll(ch);
        }

        root = this.entries.get(rootId);
    }

    public long getLargestChangeId() {
        return largestChangeId;
    }

    public void setLargestChangeId(ChangeSet changeSet, long largestChangeId) {
        this.largestChangeId = largestChangeId;
        changeSet.put(namespace, largestChangeIdEntryKey, Long.toString(largestChangeId));
    }

    public KnownFile getRoot() {
        return root;
    }

    public void setRoot(ChangeSet changeSet, FileId rootId) {

        if (root != null)
            throw new IllegalStateException("root is already set");

        File file = new File(rootId);
        file.setName("{root}");
        file.setIsDirectory(true);

        root = create(file);

        changeSet.put(namespace, rootEntryKey, rootId.get());

        put(root);
    }

    public void reset(ChangeSet changeSet) {

        if (root == null)
            return;

        FileId rootId = root.getId();

        root = null;
        entries.clear();
        uploadFailureNotificationFile = null;

        for (Map.Entry<String, String> entry : storage.getData(namespace)) {
            if (!entry.getKey().startsWith(largestChangeIdEntryKey)) {
                changeSet.remove(namespace, entry.getKey());
            }
        }

        setRoot(changeSet, rootId);
    }

    public KnownFile getUploadFailureNotificationFile() {
        return uploadFailureNotificationFile;
    }

    public void createOrUpdateUploadFailureNotificationFile(ChangeSet changeSet, Date date) {

        if (uploadFailureNotificationFile != null) {

            uploadFailureNotificationFile.setDates(changeSet, date, date);

        } else {

            File file = new File(fileIdStore.get(Uploader.uploadFailureNotificationFileId));
            file.setName(uploadFailureNotificationFileName);
            file.setCreatedDate(date);
            file.setModifiedDate(date);
            file.setAccessedDate(date);

            uploadFailureNotificationFile = create(file);

            root.tryAddChild(changeSet, uploadFailureNotificationFile);
        }
    }

    public KnownFile create(File file) {
        return new KnownFile(file, this);
    }

    public KnownFile get(FileId id) {
        return entries.get(id);
    }

    public int getFileCount() {
        return entries.size();
    }

    public int getTrackedDirCount() {
        return getTrackedDirCount(root);
    }

    private int getTrackedDirCount(KnownFile file) {

        Map<String, KnownFile> children = file.getChildrenOrNull();

        if (children == null)
            return 0;

        int result = 1;

        for (KnownFile child : children.values()) {
            result += getTrackedDirCount(child);
        }

        return result;
    }

    KnownFile put(KnownFile file) {
        return entries.put(file.getId(), file);
    }

    void remove(KnownFile file) {
        entries.remove(file.getId());
    }
}
