package jdbox.filetree;

import jdbox.utils.LifeCycleManagerResource;
import jdbox.utils.OrderedRule;
import org.junit.Before;

import java.io.IOException;

import static jdbox.filetree.FileTreeMatcher.contains;
import static org.hamcrest.MatcherAssert.assertThat;

public class BaseFileTreeWriteTest extends BaseFileTreeTest {

    @OrderedRule
    public final LifeCycleManagerResource lifeCycleManager2 =
            new LifeCycleManagerResource(errorCollector, lifeCycleManager.getModules());

    protected FileTree fileTree2;

    @Before
    public void setUp() throws IOException {

        super.setUp();

        fileTree2 = lifeCycleManager2.getInstance(FileTree.class);

        assertThat(fileTree2, contains().nothing());
    }
}
