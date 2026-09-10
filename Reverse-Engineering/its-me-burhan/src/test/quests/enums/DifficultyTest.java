package test.quests.enums;

import quests.enums.*;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.Optional;

public class DifficultyTest {

    @Test
    public void testGetDisplayName() {
        assertEquals("mudah", Difficulty.MUDAH.getDisplayName());
        assertEquals("menengah", Difficulty.MENENGAH.getDisplayName());
        assertEquals("sulit", Difficulty.SULIT.getDisplayName());
    }

    @Test
    public void testGetMinWandererLevel() {
        assertEquals(1, Difficulty.MUDAH.getMinWandererLevel());
        assertEquals(6, Difficulty.MENENGAH.getMinWandererLevel());
        assertEquals(16, Difficulty.SULIT.getMinWandererLevel());
    }

    @Test
    public void testFromStringValid() {
        assertEquals(Optional.of(Difficulty.MUDAH), Difficulty.fromString("mudah"));
        assertEquals(Optional.of(Difficulty.MENENGAH), Difficulty.fromString("menengah"));
        assertEquals(Optional.of(Difficulty.SULIT), Difficulty.fromString("sulit"));
    }

    @Test
    public void testFromStringCaseInsensitive() {
        assertEquals(Optional.of(Difficulty.MUDAH), Difficulty.fromString("MuDaH"));
        assertEquals(Optional.of(Difficulty.SULIT), Difficulty.fromString("SULIT"));
    }

    @Test
    public void testFromStringWithSpaces() {
        assertEquals(Optional.of(Difficulty.MENENGAH), Difficulty.fromString(" menengah "));
    }

    @Test
    public void testFromStringInvalid() {
        assertFalse(Difficulty.fromString("unknown").isPresent());
    }

    @Test
    public void testFromStringNull() {
        assertFalse(Difficulty.fromString(null).isPresent());
    }
}
