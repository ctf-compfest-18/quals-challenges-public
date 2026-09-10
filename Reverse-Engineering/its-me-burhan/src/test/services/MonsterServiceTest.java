package test.services;

import org.junit.Before;
import org.junit.Test;

import services.MonsterService;

import entities.monster.Monster;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Optional;

public class MonsterServiceTest {
    private MonsterService monsterService;

    @Before
    public void setUp() {
        monsterService = new MonsterService();
    }

    @Test
    public void testAddMonsterAndFetchByNumericOrPrefixedId() {
        monsterService.addMonster("Slime", 50, 10, 2, 100, 50);

        assertEquals(1, monsterService.getMonstersCount());
        assertEquals("Slime", monsterService.fetchMonsterbyId("1").getName());
        assertEquals("Slime", monsterService.fetchMonsterbyId("M1").getName());
    }


    @Test
    public void testFindMonsterByIdReturnsOptionalForExistingAndMissingMonster() {
        monsterService.addMonster("Slime", 50, 10, 2, 100, 50);

        Optional<Monster> byNumericId = monsterService.findMonsterById("1");
        Optional<Monster> byPrefixedId = monsterService.findMonsterById("M1");

        assertTrue(byNumericId.isPresent());
        assertTrue(byPrefixedId.isPresent());
        assertEquals("Slime", byNumericId.orElse(null).getName());
        assertEquals("Slime", byPrefixedId.orElse(null).getName());
        assertFalse(monsterService.findMonsterById("M99").isPresent());
        assertFalse(monsterService.findMonsterById(null).isPresent());
    }

    @Test
    public void testFindMonsterByNameReturnsOptionalForExistingAndMissingMonster() {
        monsterService.addMonster("Slime", 50, 10, 2, 100, 50);

        Optional<Monster> found = monsterService.findMonsterByName(" slime ");

        assertTrue(found.isPresent());
        assertEquals("Slime", found.orElse(null).getName());
        assertFalse(monsterService.findMonsterByName("Goblin").isPresent());
        assertFalse(monsterService.findMonsterByName(null).isPresent());
    }

    @Test
    public void testShowMethods() {
        assertEquals("Belum ada monster terdaftar\n", monsterService.showMonsters());

        monsterService.addMonster("Slime", 50, 10, 2, 100, 50);

        assertTrue(monsterService.showMonsters().contains("Nama Monster: Slime"));
        assertEquals("1. Slime\n", monsterService.showMonstersName());
    }

    @Test
    public void testValidateMonsterInputAllowsValidInput() {
        monsterService.validateMonsterInput("Goblin King", "100", "25.5", ".75", "10", "0");
    }

    @Test
    public void testValidateMonsterInputRejectsZeroAttackPower() {
        assertInvalidMonsterInput(
                "MiniSlime", "1", "0", "1", "0", "0",
                "Attack power harus bilangan positif."
        );
    }

    @Test
    public void testValidateMonsterInputRejectsZeroDefense() {
        assertInvalidMonsterInput(
                "MiniSlime", "1", "1", "0", "0", "0",
                "Defense harus bilangan positif."
        );
    }

    @Test
    public void testValidateMonsterInputRejectsInvalidName() {
        assertInvalidMonsterInput(
                "goblin King", "100", "25", "5", "10", "5",
                "Nama monster hanya boleh berisi karakter alfanumerik dan spasi, huruf pertama setiap kata harus kapital, dan selain itu tidak boleh ada huruf kapital."
        );

        assertInvalidMonsterInput(
                "Goblin-King", "100", "25", "5", "10", "5",
                "Nama monster hanya boleh berisi karakter alfanumerik dan spasi, huruf pertama setiap kata harus kapital, dan selain itu tidak boleh ada huruf kapital."
        );
    }

    @Test
    public void testValidateMonsterInputRejectsInvalidMaxHp() {
        assertInvalidMonsterInput(
                "Goblin King", "0", "25", "5", "10", "5",
                "HP maksimal harus bilangan positif."
        );
    }

    @Test
    public void testValidateMonsterInputRejectsInvalidAttackPower() {
        assertInvalidMonsterInput(
                "Goblin King", "100", "-25", "5", "10", "5",
                "Attack power harus bilangan positif."
        );
    }

    @Test
    public void testValidateMonsterInputRejectsInvalidDefense() {
        assertInvalidMonsterInput(
                "Goblin King", "100", "25", "abc", "10", "5",
                "Defense harus bilangan positif."
        );
    }

    @Test
    public void testValidateMonsterInputRejectsInvalidExpReward() {
        assertInvalidMonsterInput(
                "Goblin King", "100", "25", "5", "-1", "5",
                "Exp reward harus bilangan bulat nonnegatif."
        );
    }

    @Test
    public void testValidateMonsterInputRejectsInvalidCoinReward() {
        assertInvalidMonsterInput(
                "Goblin King", "100", "25", "5", "10", "1.5",
                "Coin reward harus bilangan bulat nonnegatif."
        );
    }

    private void assertInvalidMonsterInput(
            String name,
            String maxHp,
            String attackPower,
            String defense,
            String expReward,
            String coinReward,
            String expectedMessage
    ) {
        try {
            monsterService.validateMonsterInput(name, maxHp, attackPower, defense, expReward, coinReward);
            fail("validateMonsterInput seharusnya melempar IllegalArgumentException.");
        } catch (IllegalArgumentException e) {
            assertEquals(expectedMessage, e.getMessage());
        }
    }
}
