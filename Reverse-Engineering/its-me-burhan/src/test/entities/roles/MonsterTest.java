package test.entities.roles;

import entities.monster.*;

import org.junit.Test;
import static org.junit.Assert.*;

public class MonsterTest {

    @Test
    public void testBleedCreatesNoteAndTriggersOnlyOnce() {
        Monster monster = new Monster(1, "Slime", 100, 10, 2, 50, 10);

        monster.applyBleed("Assassin", 20);
        monster.onTurnStart();

        assertEquals(80.0, monster.getCurrentHp(), 0.001);
        assertEquals(1, monster.getBleedTriggeredCount());

        String note = monster.consumeCustomDamageNote();
        assertNotNull(note);
        assertTrue(note.contains("Bleed"));

        assertNull(monster.consumeCustomDamageNote());

        monster.onTurnStart();
        assertEquals(80.0, monster.getCurrentHp(), 0.001);
        assertEquals(1, monster.getBleedTriggeredCount());
    }

    @Test
    public void testResetBattleStateClearsBleedAndSummary() {
        Monster monster = new Monster(1, "Slime", 100, 10, 2, 50, 10);

        monster.applyBleed("Assassin", 20);
        monster.resetBattleState();
        monster.onTurnStart();

        assertEquals(100.0, monster.getCurrentHp(), 0.001);
        assertEquals(0, monster.getBleedTriggeredCount());
        assertNull(monster.consumeCustomDamageNote());
    }
}