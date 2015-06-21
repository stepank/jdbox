package jdbox.filetree;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.AnyOf.anyOf;

@Category(FileTree.class)
@RunWith(Parameterized.class)
public class NameResolutionReadTest extends BaseFileTreeTest {

    enum Type {
        FETCH,
        APPLY_CHANGES
    }

    @Parameterized.Parameter
    public Type type;

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{{Type.FETCH}, {Type.APPLY_CHANGES}});
    }

    private void tryAssertFileTreeContainsNothing() {
        if (type == Type.APPLY_CHANGES)
            assertThat(fileTree, contains().nothing());
    }

    private boolean tryUpdateFileTree() {
        if (type == Type.FETCH)
            return false;
        fileTree.update();
        return true;
    }

    /**
     * List all files, make sure that a typed file w/o extension is listed with extension.
     */
    @Test
    public void extensionIsAdded() throws Exception {

        tryAssertFileTreeContainsNothing();

        drive.createFile(testFileName, testDir, getTestPdfContent());

        tryUpdateFileTree();

        assertThat(fileTree, contains()
                .file()
                .withName(testFileName + ".pdf")
                .withRealName(testFileName));

        assertCounts(2, 1);
    }

    /**
     * List all files, make sure that a typed file with extension is listed with extension.
     */
    @Test
    public void extensionIsPreserved() throws Exception {

        tryAssertFileTreeContainsNothing();

        drive.createFile(testFileName + ".pdf", testDir, getTestPdfContent());

        tryUpdateFileTree();

        assertThat(fileTree, contains().file().withName(testFileName + ".pdf"));

        assertCounts(2, 1);
    }

    @Test
    public void sameNameNonTypedFiles() throws Exception {

        tryAssertFileTreeContainsNothing();

        drive.createFile(testFileName, testDir, getTestContent());

        if (tryUpdateFileTree())
            assertThat(fileTree, contains().file().withName(testFileName).withRealName(testFileName));

        drive.createFile(testFileName, testDir, getTestContent());
        drive.createFile(testFileName, testDir, getTestContent());

        tryUpdateFileTree();

        assertThat(fileTree, contains()
                .file().withName(testFileName).withRealName(testFileName).and()
                .file().withName(testFileName + " 2").withRealName(testFileName).and()
                .file().withName(testFileName + " 3").withRealName(testFileName));

        assertCounts(4, 1);
    }

    @Test
    public void sameNameTypedFiles() throws Exception {

        tryAssertFileTreeContainsNothing();

        drive.createFile(testFileName, testDir, getTestPdfContent());

        if (tryUpdateFileTree())
            assertThat(fileTree, contains().file().withName(testFileName + ".pdf").withRealName(testFileName));

        drive.createFile(testFileName, testDir, getTestPdfContent());
        drive.createFile(testFileName, testDir, getTestPdfContent());

        tryUpdateFileTree();

        assertThat(fileTree, contains()
                .file().withName(testFileName + ".pdf").withRealName(testFileName).and()
                .file().withName(testFileName + " 2.pdf").withRealName(testFileName).and()
                .file().withName(testFileName + " 3.pdf").withRealName(testFileName));

        assertCounts(4, 1);
    }

    @Test
    public void sameNameMixedFiles() throws Exception {

        tryAssertFileTreeContainsNothing();

        drive.createFile(testFileName, testDir, getTestContent());

        if (tryUpdateFileTree())
            assertThat(fileTree, contains().file().withName(testFileName).withRealName(testFileName));

        drive.createFile(testFileName, testDir, getTestPdfContent());

        tryUpdateFileTree();

        assertThat(fileTree, contains()
                .file().withName(testFileName).withRealName(testFileName).and()
                .file().withName(testFileName + ".pdf").withRealName(testFileName));

        assertCounts(3, 1);
    }

    @Test
    public void sameNameWithExtensions() throws Exception {

        tryAssertFileTreeContainsNothing();

        drive.createFile(testFileName + ".pdf", testDir, getTestContent());

        if (tryUpdateFileTree())
            assertThat(fileTree, contains().file().withName(testFileName + ".pdf").withRealName(testFileName + ".pdf"));

        drive.createFile(testFileName + ".pdf", testDir, getTestContent());

        tryUpdateFileTree();

        assertThat(fileTree, contains()
                .file().withName(testFileName + ".pdf").withRealName(testFileName + ".pdf").and()
                .file().withName(testFileName + " 2.pdf").withRealName(testFileName + ".pdf"));

        assertCounts(3, 1);
    }

    @Test
    public void sameNameWithAndWoExtension() throws Exception {

        tryAssertFileTreeContainsNothing();

        drive.createFile(testFileName, testDir, getTestPdfContent());

        if (tryUpdateFileTree())
            assertThat(fileTree, contains().file().withName(testFileName + ".pdf").withRealName(testFileName));

        drive.createFile(testFileName + ".pdf", testDir, getTestContent());

        tryUpdateFileTree();

        // we don't know what number each of the files will have,
        // so we check that at least one of these asserts passes
        assertThat(fileTree, anyOf(
                contains()
                        .file().withName(testFileName + ".pdf").withRealName(testFileName).and()
                        .file().withName(testFileName + " 2.pdf").withRealName(testFileName + ".pdf"),
                contains()
                        .file().withName(testFileName + ".pdf").withRealName(testFileName + ".pdf").and()
                        .file().withName(testFileName + " 2.pdf").withRealName(testFileName)
        ));

        assertCounts(3, 1);
    }
}
