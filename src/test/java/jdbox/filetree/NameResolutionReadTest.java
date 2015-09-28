package jdbox.filetree;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

import static jdbox.filetree.FileTreeMatcher.contains;
import static jdbox.utils.TestUtils.*;
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
    public void extensionIsAdded() throws IOException {

        tryAssertFileTreeContainsNothing();

        drive.createFile(getTestFileName(), testFolder, getTestPdfContent());

        tryUpdateFileTree();

        assertThat(fileTree, contains()
                .file()
                .withName(getTestFileName() + ".pdf")
                .withRealName(getTestFileName()));

        assertCounts(2, 1);
    }

    /**
     * List all files, make sure that a typed file with extension is listed with extension.
     */
    @Test
    public void extensionIsPreserved() throws IOException {

        tryAssertFileTreeContainsNothing();

        drive.createFile(getTestFileName() + ".pdf", testFolder, getTestPdfContent());

        tryUpdateFileTree();

        assertThat(fileTree, contains().file().withName(getTestFileName() + ".pdf"));

        assertCounts(2, 1);
    }

    @Test
    public void sameNameNonTypedFiles() throws IOException {

        tryAssertFileTreeContainsNothing();

        drive.createFile(getTestFileName(), testFolder, getTestContent());

        if (tryUpdateFileTree())
            assertThat(fileTree, contains().file().withName(getTestFileName()).withRealName(getTestFileName()));

        drive.createFile(getTestFileName(), testFolder, getTestContent());
        drive.createFile(getTestFileName(), testFolder, getTestContent());

        tryUpdateFileTree();

        assertThat(fileTree, contains()
                .file().withName(getTestFileName()).withRealName(getTestFileName()).and()
                .file().withName(getTestFileName() + " 2").withRealName(getTestFileName()).and()
                .file().withName(getTestFileName() + " 3").withRealName(getTestFileName()));

        assertCounts(4, 1);
    }

    @Test
    public void sameNameTypedFiles() throws IOException {

        tryAssertFileTreeContainsNothing();

        drive.createFile(getTestFileName(), testFolder, getTestPdfContent());

        if (tryUpdateFileTree())
            assertThat(fileTree, contains()
                    .file().withName(getTestFileName() + ".pdf").withRealName(getTestFileName()));

        drive.createFile(getTestFileName(), testFolder, getTestPdfContent());
        drive.createFile(getTestFileName(), testFolder, getTestPdfContent());

        tryUpdateFileTree();

        assertThat(fileTree, contains()
                .file().withName(getTestFileName() + ".pdf").withRealName(getTestFileName()).and()
                .file().withName(getTestFileName() + " 2.pdf").withRealName(getTestFileName()).and()
                .file().withName(getTestFileName() + " 3.pdf").withRealName(getTestFileName()));

        assertCounts(4, 1);
    }

    @Test
    public void sameNameMixedFiles() throws IOException {

        tryAssertFileTreeContainsNothing();

        drive.createFile(getTestFileName(), testFolder, getTestContent());

        if (tryUpdateFileTree())
            assertThat(fileTree, contains().file().withName(getTestFileName()).withRealName(getTestFileName()));

        drive.createFile(getTestFileName(), testFolder, getTestPdfContent());

        tryUpdateFileTree();

        assertThat(fileTree, contains()
                .file().withName(getTestFileName()).withRealName(getTestFileName()).and()
                .file().withName(getTestFileName() + ".pdf").withRealName(getTestFileName()));

        assertCounts(3, 1);
    }

    @Test
    public void sameNameWithExtensions() throws IOException {

        tryAssertFileTreeContainsNothing();

        drive.createFile(getTestFileName() + ".pdf", testFolder, getTestContent());

        if (tryUpdateFileTree())
            assertThat(fileTree, contains()
                    .file().withName(getTestFileName() + ".pdf").withRealName(getTestFileName() + ".pdf"));

        drive.createFile(getTestFileName() + ".pdf", testFolder, getTestContent());

        tryUpdateFileTree();

        assertThat(fileTree, contains()
                .file().withName(getTestFileName() + ".pdf").withRealName(getTestFileName() + ".pdf").and()
                .file().withName(getTestFileName() + " 2.pdf").withRealName(getTestFileName() + ".pdf"));

        assertCounts(3, 1);
    }

    @Test
    public void sameNameWithAndWoExtension() throws IOException {

        tryAssertFileTreeContainsNothing();

        drive.createFile(getTestFileName(), testFolder, getTestPdfContent());

        if (tryUpdateFileTree())
            assertThat(fileTree, contains()
                    .file().withName(getTestFileName() + ".pdf").withRealName(getTestFileName()));

        drive.createFile(getTestFileName() + ".pdf", testFolder, getTestContent());

        tryUpdateFileTree();

        // we don't know what number each of the files will have,
        // so we check that at least one of these asserts passes
        assertThat(fileTree, anyOf(
                contains()
                        .file().withName(getTestFileName() + ".pdf").withRealName(getTestFileName()).and()
                        .file().withName(getTestFileName() + " 2.pdf").withRealName(getTestFileName() + ".pdf"),
                contains()
                        .file().withName(getTestFileName() + ".pdf").withRealName(getTestFileName() + ".pdf").and()
                        .file().withName(getTestFileName() + " 2.pdf").withRealName(getTestFileName())
        ));

        assertCounts(3, 1);
    }
}
