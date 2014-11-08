package jdbox;

import java.util.Map;

public class File {

    public static String fields = "items(id,title,mimeType,downloadUrl,fileSize,alternateLink)";

    private static String alternateLinkText =
            "This file cannot be donloaded directly, you can open it in browser using the following link:\n  ";

    private final com.google.api.services.drive.model.File file;

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

    public boolean isDownloadable() {
        return file.getDownloadUrl() != null && file.getDownloadUrl().length() != 0;
    }

    public long getSize() {
        if (isDirectory())
            return 0;
        if (!isDownloadable())
            return getExportInfo().length();
        Long size = file.getFileSize();
        return size == null ? 0 : size;
    }

    public String getDownloadUrl() {
        return file.getDownloadUrl();
    }

    public String getExportInfo() {
        return alternateLinkText + file.getAlternateLink() + "\n";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        File file1 = (File) o;

        if (file != null ? !file.equals(file1.file) : file1.file != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return file != null ? file.getId().hashCode() : 0;
    }

    public String toString() {
        return String.format("file %s", getName());
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

    public boolean isDownloadable() {
        return false;
    }

    public String getDownloadUrl() {
        return null;
    }

    public Map<String, String> getExportLinks() {
        return null;
    }

    public long getSize() {
        return 0;
    }
}