package jdbox.filetree;

import com.google.inject.Injector;
import org.junit.Before;
import org.junit.experimental.categories.Category;

@Category(FileTree.class)
public class BaseFileTreeWriteTest extends BaseFileTreeTest {

    protected FileTree fileTree2;

    @Before
    public void setUp() throws Exception {

        super.setUp();

        Injector injector2 = createInjector();

        fileTree2 = injector2.getInstance(FileTree.class);
        fileTree2.setRoot(testDir);

        assertFileTreeContains(fileTree2).nothing();
    }
}
