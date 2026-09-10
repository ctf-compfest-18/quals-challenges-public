package exception;

public class InvalidFormatException extends DataFileException {
    private final String path;

    public InvalidFormatException(String path) {
        super("Format file tidak valid: " + path);
        this.path = path;
    }

    public InvalidFormatException(String path, String detail) {
        super("Format file tidak valid pada " + path + ": " + detail);
        this.path = path;
    }

    public String getPath() {
        return path;
    }
}
