package test.exception;

import exception.*;

import org.junit.Test;
import static org.junit.Assert.*;

public class InsufficientLevelExceptionTest {

    @Test
    public void testConstructorStoresCurrentAndMinimumLevel() {
        InsufficientLevelException exception = new InsufficientLevelException(2, 5);

        assertEquals(2, exception.getCurrentLevel());
        assertEquals(5, exception.getMinimumLevel());
    }

    @Test
    public void testMessageContainsCurrentAndMinimumLevel() {
        InsufficientLevelException exception = new InsufficientLevelException(2, 5);

        assertTrue(exception.getMessage().contains("2"));
        assertTrue(exception.getMessage().contains("5"));
    }

    @Test
    public void testIsChildOfBurhanQuestException() {
        InsufficientLevelException exception = new InsufficientLevelException(1, 10);

        assertTrue(exception instanceof BurhanQuestException);
        assertTrue(exception instanceof Exception);
    }

    @Test
    public void testCanBeCaughtAsBurhanQuestException() {
        try {
            throw new InsufficientLevelException(1, 10);
        } catch (BurhanQuestException exception) {
            assertTrue(exception instanceof InsufficientLevelException);
            assertTrue(exception.getMessage().contains("1"));
            assertTrue(exception.getMessage().contains("10"));
            return;
        }
    }
}