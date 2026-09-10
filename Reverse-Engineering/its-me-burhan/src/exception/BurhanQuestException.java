package exception;

public class BurhanQuestException extends Exception {
    public BurhanQuestException(String message) {
        super(message);
    }

    public BurhanQuestException(String message, Throwable cause) {
        super(message, cause);
    }
}