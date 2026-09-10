package test.exception;

import exception.*;

import org.junit.Test;
import static org.junit.Assert.*;

public class BurhanQuestExceptionTest {

    @Test
    public void testConstructorWithMessage() {
        BurhanQuestException exception = new BurhanQuestException("Terjadi error BurhanQuest");

        assertEquals("Terjadi error BurhanQuest", exception.getMessage());
        assertNull(exception.getCause());
        assertTrue(exception instanceof Exception);
    }

    @Test
    public void testConstructorWithMessageAndCause() {
        RuntimeException cause = new RuntimeException("Root cause");
        BurhanQuestException exception = new BurhanQuestException("Terjadi error BurhanQuest", cause);

        assertEquals("Terjadi error BurhanQuest", exception.getMessage());
        assertSame(cause, exception.getCause());
        assertTrue(exception instanceof Exception);
    }
}