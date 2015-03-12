package jdbox.openedfiles;

import jdbox.BaseTest;
import jdbox.Uploader;
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
