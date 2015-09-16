package jdbox.utils;

import jdbox.filetree.FileTree;
import org.junit.rules.ExternalResource;

public class TestFolderIsolation extends ExternalResource {

    private final LifeCycleManagerResource lifeCycleManager;
    private final TestFolderProvider testFolderProvider;

    public TestFolderIsolation(LifeCycleManagerResource lifeCycleManager, TestFolderProvider testFolderProvider) {
        this.lifeCycleManager = lifeCycleManager;
        this.testFolderProvider = testFolderProvider;
    }

    public void before() throws InterruptedException {
        lifeCycleManager.getInstance(FileTree.class).setRoot(testFolderProvider.getTestFolder().getId());
    }
}
