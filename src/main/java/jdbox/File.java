package jdbox;

public class File {

    private com.google.api.services.drive.model.File file;

    public File(com.google.api.services.drive.model.File file) {
        this.file = file;
    }

    public String getId() {
        return file.getId();
    }

    public String getName() {
        return file.getTitle();
    }

    public boolean isDirectory() {
        return file.getMimeType().equals("application/vnd.google-apps.folder");
    }

    public long getSize() {
        Long size = file.getFileSize();
        return size == null ? 0 : size;
    }
}

class Root extends File {

    public static final String ROOT_ID = "root";

    public Root() {
        super(null);
    }

    public String getId() {
        return ROOT_ID;
    }

    public String getName() {
        return "/";
    }

    public boolean isDirectory() {
        return true;
    }

    public long getSize() {
        return 0;
    }
}