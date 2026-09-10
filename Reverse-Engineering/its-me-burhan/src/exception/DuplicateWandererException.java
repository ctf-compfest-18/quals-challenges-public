package exception;

public class DuplicateWandererException extends BurhanQuestException {
    public DuplicateWandererException(String username) {
        super("Username '" + username + "' sudah digunakan.");
    }

    public DuplicateWandererException(String username, boolean fromImport) {
        super(fromImport
                ? "duplikasi wanderer terdeteksi:\n" + username
                : "Username '" + username + "' sudah digunakan.");
    }
}