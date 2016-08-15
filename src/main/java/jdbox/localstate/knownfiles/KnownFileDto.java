package jdbox.localstate.knownfiles;

import jdbox.models.File;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

public class KnownFileDto {

    public File file;

    public boolean isTracked;

    public KnownFileDto(KnownFile knownFile) {
        file = knownFile.toFile();
        isTracked = knownFile.isTracked();
    }

    public String serialize() {
        throw new NotImplementedException();
    }

    public static KnownFileDto deserialize(String data) {
        throw new NotImplementedException();
    }
}
