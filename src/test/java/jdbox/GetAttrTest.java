package jdbox;

import net.fusejna.ErrorCodes;
import net.fusejna.Export;
import net.fusejna.StatHolder;
import net.fusejna.types.TypeMode;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class GetAttrTest extends BaseFileSystemTest {

    @Test
    public void nonExistent() throws Exception {
        assertThat(fs.getattr("/does_not_exist", Export.createStatHolder().wrapper), equalTo(-ErrorCodes.ENOENT()));
    }

    @Test
    public void file() throws Exception {
        StatHolder stat = Export.createStatHolder();
        assertThat(fs.getattr("/test.txt", stat.wrapper), equalTo(0));
        assertThat(stat.size(), equalTo(JdBox.class.getResourceAsStream("/test.txt").available()));
        assertThat(stat.type(), equalTo(TypeMode.NodeType.FILE));
    }

    @Test
    public void directory() throws Exception {
        StatHolder stat = Export.createStatHolder();
        assertThat(fs.getattr("/test_dir", stat.wrapper), equalTo(0));
        assertThat(stat.size(), equalTo(0));
        assertThat(stat.type(), equalTo(TypeMode.NodeType.DIRECTORY));
    }
}
