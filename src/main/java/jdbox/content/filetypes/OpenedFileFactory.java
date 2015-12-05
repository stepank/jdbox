package jdbox.content.filetypes;

import jdbox.content.bytestores.ByteStore;
import jdbox.models.File;

import java.io.IOException;

public interface OpenedFileFactory {

    long getSize(File file);

    ByteStore create(File file) throws IOException;
}
