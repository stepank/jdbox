package jdbox.openedfiles;

import jdbox.BaseTest;
import org.junit.Before;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class BaseRangeMappedOpenedFileTest extends BaseTest {

    protected RangeMappedOpenedFileFactory factory;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        factory = injector.getInstance(RangeMappedOpenedFileFactory.class);
        factory.setConfig(new RangeMappedOpenedFileFactory.Config(4));
        assertThat(factory.getSharedFilesCount(), equalTo(0));
    }
}
