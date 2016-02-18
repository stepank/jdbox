package jdbox.driveadapter;

import com.google.api.services.drive.model.Change;
import com.google.api.services.drive.model.ChangeList;
import com.google.common.base.Function;
import com.google.common.collect.Lists;

import java.util.List;

public class Changes {

    public final long largestChangeId;
    public final List<jdbox.driveadapter.Change> items;

    public Changes(ChangeList changes) {
        largestChangeId = changes.getLargestChangeId();
        items = Lists.transform(changes.getItems(), new Function<Change, jdbox.driveadapter.Change>() {
            @Override
            public jdbox.driveadapter.Change apply(Change change) {
                return new jdbox.driveadapter.Change(change);
            }
        });
    }
}
