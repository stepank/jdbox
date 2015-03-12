package jdbox.openedfiles;

import jdbox.BaseTest;
import jdbox.filetree.File;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.util.Random;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class BaseRollingReadOpenedFileTest extends BaseTest {

    protected RangeMappedOpenedFileFactory readerFactory;
    protected RollingReadOpenedFileFactory factory;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        readerFactory = injector.getInstance(RangeMappedOpenedFileFactory.class);
        factory = injector.getInstance(RollingReadOpenedFileFactory.class);
    }
}
