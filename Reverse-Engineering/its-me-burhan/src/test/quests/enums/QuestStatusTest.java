package test.quests.enums;

import quests.enums.*;

import org.junit.Test;
import static org.junit.Assert.*;

public class QuestStatusTest {

    @Test
    public void testGetDisplayName() {
        assertEquals("tersedia", QuestStatus.TERSEDIA.getDisplayName());
        assertEquals("selesai", QuestStatus.SELESAI.getDisplayName());
    }

    @Test
    public void testFromStringValid() {
        assertEquals(QuestStatus.TERSEDIA, QuestStatus.fromString("tersedia"));
        assertEquals(QuestStatus.SELESAI, QuestStatus.fromString("selesai"));
    }

    @Test
    public void testFromStringCaseInsensitive() {
        assertEquals(QuestStatus.TERSEDIA, QuestStatus.fromString("TeRsEdIa"));
        assertEquals(QuestStatus.SELESAI, QuestStatus.fromString("SELESAI"));
    }

    @Test
    public void testFromStringWithSpaces() {
        assertEquals(QuestStatus.TERSEDIA, QuestStatus.fromString(" tersedia "));
    }

    @Test
    public void testFromStringInvalid() {
        assertNull(QuestStatus.fromString("unknown"));
    }

    @Test
    public void testFromStringNull() {
        assertNull(QuestStatus.fromString(null));
    }
}