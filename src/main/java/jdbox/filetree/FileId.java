package jdbox.filetree;

public class FileId implements Comparable {

    private volatile String id;

    public FileId() {
    }

    public FileId(String id) {
        this.id = id;
    }

    public String get() {
        return id;
    }

    public void set(String id) {
        if (isSet())
            throw new UnsupportedOperationException("id can be set only once");
        this.id = id;
    }

    public boolean isSet() {
        return id != null;
    }

    @Override
    public boolean equals(Object o) {

        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        FileId other = (FileId) o;

        return id != null ? id.equals(other.id) : other.id == null;
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "id " + id;
    }

    @Override
    public int compareTo(Object o) {

        if (this == o) return 0;

        if (o == null)
            throw new NullPointerException();

        FileId other = (FileId) o;

        if (id == null || other.id == null)
            throw new ClassCastException();

        return id.compareTo(other.id);
    }
}