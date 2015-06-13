package jdbox;

import net.fusejna.ErrorCodes;
import net.fusejna.Export;
import net.fusejna.StatHolder;
import net.fusejna.types.TypeMode;
import org.junit.Test;

import java.nio.file.Paths;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class GetAttrTest extends BaseFileSystemTest {

    @Test
    public void nonExistent() throws Exception {
        assertThat(fs.getattr("/does_not_exist", Export.createStatHolder().wrapper), equalTo(-ErrorCodes.ENOENT()));
    }

    @Test
    public void file() throws Exception {
        drive.createFile(testFileName, testDir, getTestContent());
        StatHolder stat = Export.createStatHolder();
        String path = Paths.get("/").resolve(testFileName).toString();
        assertThat(fs.getattr(path, stat.wrapper), equalTo(0));
        assertThat(stat.size(), equalTo(testContentString.length()));
        assertThat(stat.type(), equalTo(TypeMode.NodeType.FILE));
    }

    @Test
    public void directory() throws Exception {
        drive.createFolder(testFolderName, testDir);
        StatHolder stat = Export.createStatHolder();
        String path = Paths.get("/").resolve(testFolderName).toString();
        assertThat(fs.getattr(path, stat.wrapper), equalTo(0));
        assertThat(stat.size(), equalTo(0));
        assertThat(stat.type(), equalTo(TypeMode.NodeType.DIRECTORY));
    }
}
