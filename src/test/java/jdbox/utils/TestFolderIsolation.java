package jdbox.utils;

import jdbox.filetree.FileTree;
import org.junit.rules.ExternalResource;

public class TestFolderIsolation extends ExternalResource {

    private final InjectorProvider injectorProvider;
    private final TestFolderProvider testFolderProvider;

    public TestFolderIsolation(InjectorProvider injectorProvider, TestFolderProvider testFolderProvider) {
        this.injectorProvider = injectorProvider;
        this.testFolderProvider = testFolderProvider;
    }

    public void before() throws InterruptedException {
        injectorProvider.getInjector().getInstance(FileTree.class).setRoot(testFolderProvider.getTestFolder().getId());
    }
}
