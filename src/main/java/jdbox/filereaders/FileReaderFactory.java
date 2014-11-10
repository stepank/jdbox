package jdbox.filereaders;

import jdbox.DriveAdapter;
import jdbox.File;

import java.util.concurrent.ScheduledExecutorService;

public class FileReaderFactory {

    public static FileReader create(File file, DriveAdapter drive, ScheduledExecutorService executor) {
        if (file.getSize() <= 16 * 1024 * 1024)
            return RangeConstrainedFileReader.create(file, drive, executor);
        return new RollingFileReader(file, drive, executor);
    }
}
