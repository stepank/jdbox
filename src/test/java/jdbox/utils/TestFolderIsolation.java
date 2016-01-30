package jdbox.utils;

import jdbox.localstate.LocalState;
import jdbox.utils.fixtures.Fixture;
import org.junit.rules.ExternalResource;

public class TestFolderIsolation extends ExternalResource implements Fixture {

    private final LifeCycleManagerResource lifeCycleManager;
    private final TestFolderProvider testFolderProvider;

    public TestFolderIsolation(LifeCycleManagerResource lifeCycleManager, TestFolderProvider testFolderProvider) {
        this.lifeCycleManager = lifeCycleManager;
        this.testFolderProvider = testFolderProvider;
    }

    @Override
    public void before() {
        lifeCycleManager.getInstance(LocalState.class).setRoot(testFolderProvider.getTestFolder().getId());
    }

    @Override
    public void after() {
    }
}
