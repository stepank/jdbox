package jdbox.utils;

import jdbox.localstate.LocalState;
import org.junit.rules.ExternalResource;

public class TestFolderIsolation extends ExternalResource {

    private final LifeCycleManagerResource lifeCycleManager;
    private final TestFolderProvider testFolderProvider;

    public TestFolderIsolation(LifeCycleManagerResource lifeCycleManager, TestFolderProvider testFolderProvider) {
        this.lifeCycleManager = lifeCycleManager;
        this.testFolderProvider = testFolderProvider;
    }

    public void before() {
        lifeCycleManager.getInstance(LocalState.class).setRoot(testFolderProvider.getTestFolder().getId());
    }
}
