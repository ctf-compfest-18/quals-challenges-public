package test.exception;

import exception.*;

import static org.junit.Assert.*;

import org.junit.Test;

public class DuplicateWandererExceptionTest {

    @Test
    public void testConstructorCreatesNormalDuplicateUsernameMessage() {
        DuplicateWandererException exception = new DuplicateWandererException("frieren");

        assertEquals("Username 'frieren' sudah digunakan.", exception.getMessage());
    }

    @Test
    public void testConstructorWithFromImportFalseCreatesNormalDuplicateUsernameMessage() {
        DuplicateWandererException exception = new DuplicateWandererException("himmel", false);

        assertEquals("Username 'himmel' sudah digunakan.", exception.getMessage());
    }

    @Test
    public void testConstructorWithFromImportTrueCreatesImportDuplicateMessage() {
        DuplicateWandererException exception = new DuplicateWandererException("frieren", true);

        assertEquals("duplikasi wanderer terdeteksi:\nfrieren", exception.getMessage());
    }

    @Test
    public void testDuplicateWandererExceptionCanBeCaughtAsBurhanQuestException() {
        try {
            throw new DuplicateWandererException("stark");
        } catch (BurhanQuestException exception) {
            assertEquals("Username 'stark' sudah digunakan.", exception.getMessage());
            return;
        }
    }
}