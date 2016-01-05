package jdbox.filetree;

import jdbox.utils.LifeCycleManagerResource;
import jdbox.utils.OrderedRule;
import jdbox.utils.TestFolderIsolation;
import org.junit.Before;

import static jdbox.filetree.FileTreeMatcher.contains;
import static org.hamcrest.MatcherAssert.assertThat;

public class BaseFileTreeWriteTest extends BaseFileTreeTest {

    @OrderedRule(1)
    public final LifeCycleManagerResource lifeCycleManager2 =
            new LifeCycleManagerResource(errorCollector, lifeCycleManager.getModules());

    @OrderedRule(2)
    public final TestFolderIsolation testFolderIsolation =
            new TestFolderIsolation(lifeCycleManager2, testFolderProvider);

    protected FileTree fileTree2;

    @Before
    public void setUp() {

        super.setUp();

        fileTree2 = lifeCycleManager2.getInstance(FileTree.class);

        assertThat(fileTree2, contains().nothing());
    }
}
