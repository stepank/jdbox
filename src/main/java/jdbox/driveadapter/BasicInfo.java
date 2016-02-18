package jdbox.driveadapter;

import com.google.api.services.drive.model.About;

public class BasicInfo {

    public final String rootFolderId;
    public final long largestChangeId;

    public BasicInfo(String rootFolderId, long largestChangeId) {
        this.rootFolderId = rootFolderId;
        this.largestChangeId = largestChangeId;
    }

    public BasicInfo(About about) {
        rootFolderId = about.getRootFolderId();
        largestChangeId = about.getLargestChangeId();
    }
}
