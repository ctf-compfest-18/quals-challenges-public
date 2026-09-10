package test.entities;

import entities.*;
import entities.monster.*;
import quests.*;
import quests.enums.*;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Optional;

public class WandererTest {

    @Test
    public void testConstructorAndGetters() {
        Wanderer w = new Wanderer(1, "Hero", "hero", "pass", 100, 20, 5);

        assertEquals("P1", w.getId());
        assertEquals("Hero", w.getName());
        assertEquals("hero", w.getUsername());
        assertEquals(1, w.getLevel());
        assertEquals(0, w.getExp());
        assertEquals(0, w.getCoins());
        assertEquals(100.0, w.getCurrentHp(), 0.001);
        assertEquals(100.0, w.getMaxHp(), 0.001);
        assertEquals(20.0, w.getAttackPower(), 0.001);
        assertEquals(5.0, w.getDefense(), 0.001);
    }

    @Test
    public void testCanTakeQuest() {
        Wanderer w = new Wanderer(1, "Hero", "hero", "pass", 100, 20, 5);

        assertTrue(w.canTakeQuest(Difficulty.MUDAH));
        assertFalse(w.canTakeQuest(Difficulty.MENENGAH));
        assertFalse(w.canTakeQuest(Difficulty.SULIT));
    }

    @Test
    public void testAddExpAndCoins() {
        Wanderer w = new Wanderer(1, "Hero", "hero", "pass", 100, 20, 5);

        w.addExp(500);
        w.addCoins(100);

        assertEquals(500, w.getExp());
        assertEquals(100, w.getCoins());
    }

    @Test
    public void testCompleteQuestAddsRewardAndLevelsUp() {
        Wanderer w = new Wanderer(1, "Hero", "hero", "pass", 100, 20, 5);

        w.completeQuest(5000, 100);

        assertEquals(5000, w.getExp());
        assertEquals(100, w.getCoins());
        assertEquals(2, w.getLevel());
    }

    @Test
    public void testCompleteQuestCapsAtLevel20() {
        Wanderer w = new Wanderer(1, "Hero", "hero", "pass", 100, 20, 5);

        w.completeQuest(2_000_000_000, 100);

        assertEquals(20, w.getLevel());
        assertEquals(1_310_720_000, w.getExp());
        assertEquals(100, w.getCoins());
    }

    
    @Test
    public void testCompleteQuestUsesCapAndLevelLimit() {
        Wanderer wanderer = new Wanderer(1, "Hero", "hero", "pass", 100, 20, 5);

        wanderer.completeQuest(2_000_000_000, 100);

        assertEquals(1_310_720_000, wanderer.getExp());
        assertEquals(20, wanderer.getLevel());
        assertEquals(100, wanderer.getCoins());
    }

    @Test
    public void testGetNextLevelExp() {
        Wanderer w = new Wanderer(1, "Hero", "hero", "pass", 100, 20, 5);

        assertEquals(5000, w.getNextLevelExp(1));
        assertEquals(10000, w.getNextLevelExp(2));
        assertEquals(20000, w.getNextLevelExp(3));
    }

    @Test
    public void testHpMethods() {
        Wanderer w = new Wanderer(1, "Hero", "hero", "pass", 100, 20, 5);

        w.takeDamage(40);
        assertEquals(60.0, w.getCurrentHp(), 0.001);

        w.resetCurrentHp();
        assertEquals(100.0, w.getCurrentHp(), 0.001);

        w.setCurrentHp(150);
        assertEquals(100.0, w.getCurrentHp(), 0.001);
    }

        private static class TestableWanderer extends Wanderer {
        public TestableWanderer(
                int idNumber,
                String name,
                String username,
                String password,
                double maxHp,
                double attackPower,
                double defense
        ) {
            super(idNumber, name, username, password, maxHp, attackPower, defense);
        }

        public Optional<Monster> exposeCurrentTarget() {
            return getCurrentTarget();
        }

        public double exposeCurrentMultiplier() {
            return getCurrentMultiplier();
        }

        public void exposeSetCustomDamageNote(String note) {
            setCustomDamageNote(note);
        }
    }

    @Test
    public void testBattleContextAndCustomNote() {
        TestableWanderer w = new TestableWanderer(1, "Hero", "hero", "pass", 100, 20, 5);
        Monster m = new Monster(1, "Slime", 50, 5, 1, 10, 5);

        w.setBattleContext(m, 1.25);
        w.exposeSetCustomDamageNote("note");

        assertEquals(Optional.of(m), w.exposeCurrentTarget());
        assertEquals(1.25, w.exposeCurrentMultiplier(), 0.001);
        assertEquals("note", w.consumeCustomDamageNote());
        assertNull(w.consumeCustomDamageNote());

        w.resetBattleState();

        assertFalse(w.exposeCurrentTarget().isPresent());
        assertEquals(1.0, w.exposeCurrentMultiplier(), 0.001);
        assertNull(w.consumeCustomDamageNote());
    }

    @Test
    public void testDefaultLifecycleHooks() {
        Wanderer w = new Wanderer(1, "Hero", "hero", "pass", 100, 20, 5);

        w.onTurnStart();
        assertEquals(30.0, w.modifyDamageDealt(30.0), 0.001);
        assertEquals(25.0, w.modifyDamageTaken(25.0), 0.001);
        w.onTurnEnd(30.0);

        assertFalse(w.isDefeated());
        w.takeDamage(100);
        assertTrue(w.isDefeated());
    }

    @Test
    public void testGetWelcomeMessageCombatInfoAndToString() {
        Wanderer w = new Wanderer(1, "Hero", "hero", "pass", 100, 20, 5);

        assertEquals("Login berhasil! Selamat datang, Hero.", w.getWelcomeMessage());
        assertEquals("Hero | HP: 100/100 | ATK: 20 | DEF: 5", w.getCombatInfo());
        assertTrue(w.toString().contains("ID Pengembara: P1"));
        assertTrue(w.toString().contains("Nama Pengembara: Hero"));
    }

    @Test
    public void testDefaultPassiveSummaryIsEmpty() {
        Wanderer wanderer = new Wanderer(1, "Hero", "hero", "pass", 100, 20, 5);

        assertEquals("", wanderer.getPassiveSummary());
    }


    private Wanderer createWanderer() {
        return new Wanderer(1, "Frieren", "frieren", "himmel123", 100, 50, 20);
    }

    private Quest createQuest(int idNumber, String name) {
        Monster monster = new Monster(1, "Slime", 50, 10, 2, 100, 50);

        return new Quest(idNumber, name, "Test quest", Difficulty.MUDAH, monster) {
            @Override
            public String getQuestType() {
                return "Regular";
            }

            @Override
            public String toString() {
                return getId() + " - " + getName();
            }
        };
    }

    @Test
    public void testAddCompletedQuestAddsNonNullQuest() {
        Wanderer wanderer = createWanderer();
        Quest quest = createQuest(1, "Latihan Harian");

        wanderer.addCompletedQuest(quest);

        ArrayList<Quest> history = wanderer.getCompletedQuestHistory();
        assertEquals(1, history.size());
        assertSame(quest, history.get(0));
    }

    @Test
    public void testAddCompletedQuestIgnoresNullQuest() {
        Wanderer wanderer = createWanderer();

        wanderer.addCompletedQuest(null);

        ArrayList<Quest> history = wanderer.getCompletedQuestHistory();
        assertTrue(history.isEmpty());
    }

    @Test
    public void testGetCompletedQuestHistoryReturnsCopy() {
        Wanderer wanderer = createWanderer();
        Quest quest = createQuest(1, "Latihan Harian");
        wanderer.addCompletedQuest(quest);

        ArrayList<Quest> historyCopy = wanderer.getCompletedQuestHistory();
        historyCopy.clear();

        ArrayList<Quest> actualHistory = wanderer.getCompletedQuestHistory();
        assertEquals(1, actualHistory.size());
        assertSame(quest, actualHistory.get(0));
    }

    @Test
    public void testCompletedQuestHistoryPreservesInsertionOrder() {
        Wanderer wanderer = createWanderer();
        Quest firstQuest = createQuest(1, "Latihan Harian");
        Quest secondQuest = createQuest(2, "Pembersihan Hutan");

        wanderer.addCompletedQuest(firstQuest);
        wanderer.addCompletedQuest(secondQuest);

        ArrayList<Quest> history = wanderer.getCompletedQuestHistory();
        assertEquals(2, history.size());
        assertSame(firstQuest, history.get(0));
        assertSame(secondQuest, history.get(1));
    }

    @Test
    public void testGetTitleReturnsTitleForEachLevelRange() {
        Wanderer novice = new Wanderer(1, "Hero", "hero1", "pass", 100, 20, 5);
        assertEquals("Pemula", novice.getTitle());

        Wanderer adventurer = new Wanderer(2, "Hero", "hero2", "pass", 100, 20, 5);
        adventurer.completeQuest(40_000, 0);
        assertEquals(5, adventurer.getLevel());
        assertEquals("Petualang", adventurer.getTitle());

        Wanderer knight = new Wanderer(3, "Hero", "hero3", "pass", 100, 20, 5);
        knight.completeQuest(1_280_000, 0);
        assertEquals(10, knight.getLevel());
        assertEquals("Ksatria Guild", knight.getTitle());

        Wanderer legend = new Wanderer(4, "Hero", "hero4", "pass", 100, 20, 5);
        legend.completeQuest(40_960_000, 0);
        assertEquals(15, legend.getLevel());
        assertEquals("Legenda", legend.getTitle());

        Wanderer hero = new Wanderer(5, "Hero", "hero5", "pass", 100, 20, 5);
        hero.completeQuest(1_310_720_000, 0);
        assertEquals(20, hero.getLevel());
        assertEquals("Pahlawan BurhanQuest", hero.getTitle());
    }

    @Test
    public void testRecordBattleResultAndWinRateWhenBattlesExist() {
        Wanderer wanderer = new Wanderer(1, "Hero", "hero", "pass", 100, 20, 5);

        wanderer.recordBattleResult(true);
        wanderer.recordBattleResult(false);
        wanderer.recordBattleResult(true);

        assertEquals(3, wanderer.getTotalBattles());
        assertEquals(2, wanderer.getTotalWins());
        assertEquals(1, wanderer.getTotalLosses());
        assertEquals(66.666, wanderer.getWinRate(), 0.01);
    }

    @Test
    public void testConsecutiveLossesIncrementAndResetOnWin() {
        Wanderer wanderer = new Wanderer(1, "Hero", "hero", "pass", 100, 20, 5);

        wanderer.recordBattleResult(false);
        wanderer.recordBattleResult(false);

        assertEquals(2, wanderer.getConsecutiveLosses());

        wanderer.recordBattleResult(true);

        assertEquals(0, wanderer.getConsecutiveLosses());
        assertEquals(3, wanderer.getTotalBattles());
        assertEquals(1, wanderer.getTotalWins());
        assertEquals(2, wanderer.getTotalLosses());
    }

    @Test
    public void testDemoteOneLevelMovesExpToLowerBoundOfNewLevel() {
        Wanderer wanderer = new Wanderer(1, "Hero", "hero", "pass", 100, 20, 5);

        wanderer.completeQuest(10_000, 0);
        assertEquals(3, wanderer.getLevel());

        wanderer.demoteOneLevel();

        assertEquals(2, wanderer.getLevel());
        assertEquals(5000, wanderer.getExp());
    }

    @Test
    public void testMarkEliminatedChangesEliminatedStatus() {
        Wanderer wanderer = new Wanderer(1, "Hero", "hero", "pass", 100, 20, 5);

        assertFalse(wanderer.isEliminated());

        wanderer.markEliminated();

        assertTrue(wanderer.isEliminated());
    }

}