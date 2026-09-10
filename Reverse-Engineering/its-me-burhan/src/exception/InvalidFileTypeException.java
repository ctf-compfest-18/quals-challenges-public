package exception;

public class InvalidFileTypeException extends DataFileException {
    public InvalidFileTypeException(String path) {
        super("kesalahan nama tipe file");
    }
}