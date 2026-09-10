package test.entities;

import entities.monster.Monster;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MonsterTest {

    @Test
    public void testResetHpRestoresCurrentHpToMaxHp() {
        Monster monster = new Monster(1, "Slime", 100, 20, 5, 10, 5);

        monster.takeDamage(35.5);
        assertEquals(64.5, monster.getCurrentHp(), 0.001);

        monster.resetHp();
        assertEquals(100.0, monster.getCurrentHp(), 0.001);
    }

    @Test
    public void testIsDefeatedReturnsFalseWhenHpAboveZero() {
        Monster monster = new Monster(1, "Slime", 100, 20, 5, 10, 5);

        monster.takeDamage(99.99);

        assertFalse(monster.isDefeated());
    }

    @Test
    public void testIsDefeatedReturnsTrueWhenHpIsZeroOrBelow() {
        Monster monster = new Monster(1, "Slime", 100, 20, 5, 10, 5);

        monster.takeDamage(100);
        assertTrue(monster.isDefeated());

        monster.resetHp();
        monster.takeDamage(125);
        assertTrue(monster.isDefeated());
    }

    @Test
    public void testGetCombatInfoFormatsMonsterStats() {
        Monster monster = new Monster(1, "Slime", 100, 20.25, 5.75, 10, 5);
        monster.takeDamage(12.5);

        assertEquals("Slime | HP: 87.5/100 | ATK: 20.25 | DEF: 5.75", monster.getCombatInfo());
    }
}
