package jdbox.utils;

import org.junit.rules.ExternalResource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class TempFolderProvider extends ExternalResource {

    private final List<File> folders = new ArrayList<>();

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public Path create() {
        try {
            File createdFolder = File.createTempFile("jdbox", "");
            createdFolder.delete();
            createdFolder.mkdir();
            folders.add(createdFolder);
            return createdFolder.toPath();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void after() {
        for (File file : folders) {
            recursiveDelete(file);
        }
    }

    private void recursiveDelete(File file) {

        File[] files = file.listFiles();

        if (files != null) {
            for (File each : files) {
                this.recursiveDelete(each);
            }
        }

        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }
}
