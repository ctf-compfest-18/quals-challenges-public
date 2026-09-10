package test.exception;

import exception.*;

import static org.junit.Assert.*;

import org.junit.Test;

public class InvalidFormatExceptionTest {

    @Test
    public void testInvalidFormatExceptionExtendsDataFileException() {
        InvalidFormatException exception = new InvalidFormatException("quests_import.csv");

        assertTrue(exception instanceof DataFileException);
        assertTrue(exception instanceof BurhanQuestException);
    }

    @Test
    public void testConstructorWithPathSetsMessageAndPath() {
        InvalidFormatException exception = new InvalidFormatException("quests_import.csv");

        assertEquals("Format file tidak valid: quests_import.csv", exception.getMessage());
        assertEquals("quests_import.csv", exception.getPath());
    }

    @Test
    public void testConstructorWithPathAndDetailSetsMessageAndPath() {
        InvalidFormatException exception = new InvalidFormatException(
            "quests_import.csv",
            "kolom wajib pada baris 5 kosong: name"
        );

        assertEquals(
            "Format file tidak valid pada quests_import.csv: kolom wajib pada baris 5 kosong: name",
            exception.getMessage()
        );
        assertEquals("quests_import.csv", exception.getPath());
    }

    @Test
    public void testCanBeCaughtAsDataFileException() {
        try {
            throw new InvalidFormatException("wanderers_import.csv");
        } catch (DataFileException exception) {
            assertEquals("Format file tidak valid: wanderers_import.csv", exception.getMessage());
        }
    }

    @Test
    public void testCanBeCaughtAsBurhanQuestException() {
        try {
            throw new InvalidFormatException(
                "wanderers_import.csv",
                "username pada baris 2 sudah digunakan"
            );
        } catch (BurhanQuestException exception) {
            assertEquals(
                "Format file tidak valid pada wanderers_import.csv: username pada baris 2 sudah digunakan",
                exception.getMessage()
            );
        }
    }
}