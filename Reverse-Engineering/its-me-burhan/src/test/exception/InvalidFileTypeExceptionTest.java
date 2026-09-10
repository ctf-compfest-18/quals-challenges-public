package test.exception;

import exception.*;

import static org.junit.Assert.*;

import org.junit.Test;

public class InvalidFileTypeExceptionTest {
    @Test
    public void testInvalidFileTypeExceptionIsChildOfDataFileException() {
        InvalidFileTypeException exception = new InvalidFileTypeException("data.txt");

        assertTrue(exception instanceof DataFileException);
        assertTrue(exception instanceof BurhanQuestException);
    }

    @Test
    public void testInvalidFileTypeExceptionMessage() {
        InvalidFileTypeException exception = new InvalidFileTypeException("data.txt");

        assertEquals("kesalahan nama tipe file", exception.getMessage());
    }
}