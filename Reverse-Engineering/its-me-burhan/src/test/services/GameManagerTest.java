package test.services;

import entities.*;
import entities.roles.*;
import exception.*;
import quests.*;
import quests.enums.*;
import services.*;
import test.LoggerTestHelper;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class GameManagerTest {
    private GameManager gm;
    
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private File logFile;

    @Before
    public void setUp() throws IOException {
        gm = new GameManager();
        logFile = LoggerTestHelper.isolateLogger(tempFolder);
    }

    @After
    public void tearDown() {
        LoggerTestHelper.resetLogger();
    }

    private void addThreeMonsters() {
        gm.addMonster("Slime", 50, 10, 2, 100, 50);
        gm.addMonster("Goblin", 100, 30, 10, 500, 300);
        gm.addMonster("Dragon", 300, 200, 20, 1000, 500);
    }

    private void addThreeQuests() {
        addThreeMonsters();
        gm.addQuest("Latihan Harian", "Kalahkan slime", Difficulty.MUDAH, gm.fetchMonsterbyId("1"), "1", "0", "0");
        gm.addQuest("Pembersihan Hutan", "Bersihkan hutan", Difficulty.MENENGAH, gm.fetchMonsterbyId("2"), "2", "0", "0");
        gm.addQuest("Sarang Naga", "Kalahkan naga", Difficulty.SULIT, gm.fetchMonsterbyId("3"), "3", "10000", "5000");
    }

    private Wanderer firstWanderer() {
        return (Wanderer) gm.getWanderers().get(0);
    }

    @Test
    public void testConstructorInitializesGameState() {
        assertEquals(1, gm.getCurrentDay());
        assertEquals(0, gm.getWanderersCount());
        assertEquals(0, gm.getMonstersCount());
        assertEquals(0, gm.getQuestsCount());

    }

    @Test
    public void testAddWandererCreatesCorrectJobClasses() throws DuplicateWandererException {
        gm.addWanderer("Frieren", "frieren", "himmel123", 100, 50, 20, "1");
        gm.addWanderer("Eisen", "eisen", "dwarf123", 200, 80, 40, "2");
        gm.addWanderer("Fern", "fern", "staff123", 90, 60, 10, "3");
        gm.addWanderer("Stark", "stark", "flamme123", 120, 90, 10, "4");
        gm.addWanderer("Himmel", "himmel", "hero123", 150, 70, 15, "5");
        gm.addWanderer("Heiter", "heiter", "corrupt123", 80, 30, 35, "6");

        ArrayList<User> wanderers = gm.getWanderers();

        assertEquals(6, gm.getWanderersCount());
        assertEquals("P1", ((Wanderer) wanderers.get(0)).getId());
        assertTrue(wanderers.get(0) instanceof Wanderer);
        assertTrue(wanderers.get(1) instanceof Tank);
        assertTrue(wanderers.get(2) instanceof Mage);
        assertTrue(wanderers.get(3) instanceof Assassin);
        assertTrue(wanderers.get(4) instanceof Fighter);
        assertTrue(wanderers.get(5) instanceof Support);
    }

    @Test
    public void testAddWandererJobStringReturnsCorrectJobName() {
        assertEquals("NOVICE", gm.addWandererJobString("1"));
        assertEquals("TANK", gm.addWandererJobString("2"));
        assertEquals("MAGE", gm.addWandererJobString("3"));
        assertEquals("ASSASSIN", gm.addWandererJobString("4"));
        assertEquals("FIGHTER", gm.addWandererJobString("5"));
        assertEquals("SUPPORT", gm.addWandererJobString("6"));
    }

    @Test
    public void testLoginReturnsUserOnlyWhenCredentialMatches() throws DuplicateWandererException {
        gm.addWanderer("Frieren", "frieren", "himmel123", 100, 50, 20, "3");

        User user = gm.login("frieren", "himmel123");

        assertNotNull(user);
        assertEquals("frieren", user.getUsername());
        assertNull(gm.login("frieren", "wrongpass"));
        assertNull(gm.login("unknown", "himmel123"));
    }

    @Test
    public void testUsernameCheckingMethods() throws DuplicateWandererException {
        gm.addWanderer("Frieren", "frieren", "himmel123", 100, 50, 20, "3");

        assertTrue(gm.isUsernameTaken("frieren"));
        assertFalse(gm.isUsernameTaken("fern"));

        assertFalse(gm.isUniqueUsername("frieren"));
        assertTrue(gm.isUniqueUsername("fern"));
    }

    @Test
    public void testGetWanderersReturnsCopyOfList() throws DuplicateWandererException {
        gm.addWanderer("Frieren", "frieren", "himmel123", 100, 50, 20, "3");

        ArrayList<User> wanderers = gm.getWanderers();
        wanderers.clear();

        assertEquals(1, gm.getWanderersCount());
    }

    @Test
    public void testAddMonsterIncreasesMonsterCountAndCanFetchByIdNumber() {
        addThreeMonsters();

        assertEquals(3, gm.getMonstersCount());
        assertEquals("Slime", gm.fetchMonsterbyId("1").getName());
        assertEquals("Goblin", gm.fetchMonsterbyId("2").getName());
        assertEquals("Dragon", gm.fetchMonsterbyId("3").getName());
    }

    @Test
    public void testAddQuestCreatesDailyRegularAndBountyQuest() {
        addThreeQuests();

        assertEquals(3, gm.getQuestsCount());

        assertTrue(gm.fetchQuestbyId("Q1") instanceof DailyQuest);
        assertTrue(gm.fetchQuestbyId("Q2") instanceof RegularQuest);
        assertTrue(gm.fetchQuestbyId("Q3") instanceof BountyQuest);

        assertEquals("Q1", gm.fetchQuestbyId("Q1").getId());
        assertEquals("Sarang Naga", gm.fetchQuestbyId("Q3").getName());
        assertEquals(10000, gm.fetchQuestbyId("Q3").getBonusExp());
        assertEquals(5000, gm.fetchQuestbyId("Q3").getBonusCoin());
    }

    @Test
    public void testFilterQuestByDifficultyReturnsMatchingQuestsOnly() {
        addThreeQuests();

        ArrayList<Quest> easyQuests = gm.filterQuestByDifficulty(Difficulty.MUDAH);
        ArrayList<Quest> mediumQuests = gm.filterQuestByDifficulty(Difficulty.MENENGAH);
        ArrayList<Quest> hardQuests = gm.filterQuestByDifficulty(Difficulty.SULIT);

        assertEquals(1, easyQuests.size());
        assertEquals("Latihan Harian", easyQuests.get(0).getName());
        assertEquals(1, mediumQuests.size());
        assertEquals("Pembersihan Hutan", mediumQuests.get(0).getName());
        assertEquals(1, hardQuests.size());
        assertEquals("Sarang Naga", hardQuests.get(0).getName());
    }

    @Test
    public void testFilterQuestByStatusReturnsMatchingQuestsOnly() {
        addThreeQuests();

        Quest dailyQuest = gm.fetchQuestbyId("Q1");
        Quest regularQuest = gm.fetchQuestbyId("Q2");
        regularQuest.complete();

        ArrayList<Quest> availableQuests = gm.filterQuestByStatus(dailyQuest.getStatus());
        ArrayList<Quest> completedQuests = gm.filterQuestByStatus(regularQuest.getStatus());

        assertEquals(2, availableQuests.size());
        assertEquals(1, completedQuests.size());
        assertEquals("Pembersihan Hutan", completedQuests.get(0).getName());
    }

    @Test
    public void testSortQuestByRewardAscendingAndDescending() {
        addThreeQuests();

        ArrayList<Quest> ascending = gm.sortQuestByReward(true);
        assertEquals("Latihan Harian", ascending.get(0).getName());
        assertEquals("Sarang Naga", ascending.get(2).getName());

        ArrayList<Quest> descending = gm.sortQuestByReward(false);
        assertEquals("Sarang Naga", descending.get(0).getName());
        assertEquals("Latihan Harian", descending.get(2).getName());
    }

    @Test
    public void testSortQuestByDifficultyAscendingAndDescending() {
        addThreeQuests();

        ArrayList<Quest> ascending = gm.sortQuestByDifficulty(true);
        assertEquals(Difficulty.MUDAH, ascending.get(0).getDifficulty());
        assertEquals(Difficulty.SULIT, ascending.get(2).getDifficulty());

        ArrayList<Quest> descending = gm.sortQuestByDifficulty(false);
        assertEquals(Difficulty.SULIT, descending.get(0).getDifficulty());
        assertEquals(Difficulty.MUDAH, descending.get(2).getDifficulty());
    }

    @Test
    public void testFilterWandererByLevelReturnsWanderersInsideInterval() throws DuplicateWandererException {
        gm.addWanderer("Frieren", "frieren", "himmel123", 100, 50, 20, "3");
        gm.addWanderer("Himmel", "himmel", "hero123", 150, 70, 15, "5");
        gm.addWanderer("Heiter", "heiter", "corrupt123", 80, 30, 35, "6");

        Wanderer himmel = (Wanderer) gm.getWanderers().get(1);
        himmel.completeQuest(5000, 0);

        ArrayList<User> levelOne = gm.filterWandererByLevel(1, 1);
        ArrayList<User> levelTwoToTwenty = gm.filterWandererByLevel(2, 20);

        assertEquals(2, levelOne.size());
        assertEquals(1, levelTwoToTwenty.size());
        assertEquals("Himmel", ((Wanderer) levelTwoToTwenty.get(0)).getName());
    }

    @Test
    public void testSortWandererByNameAscendingAndDescending() throws DuplicateWandererException {
        gm.addWanderer("Frieren", "frieren", "himmel123", 100, 50, 20, "3");
        gm.addWanderer("Himmel", "himmel", "hero123", 150, 70, 15, "5");
        gm.addWanderer("Eisen", "eisen", "dwarf123", 200, 80, 40, "2");

        ArrayList<User> ascending = gm.sortWandererByName(true);
        assertEquals("Eisen", ((Wanderer) ascending.get(0)).getName());
        assertEquals("Himmel", ((Wanderer) ascending.get(2)).getName());

        ArrayList<User> descending = gm.sortWandererByName(false);
        assertEquals("Himmel", ((Wanderer) descending.get(0)).getName());
        assertEquals("Eisen", ((Wanderer) descending.get(2)).getName());
    }

    @Test
    public void testSortWandererByLevelAscendingAndDescending() throws DuplicateWandererException {
        gm.addWanderer("Frieren", "frieren", "himmel123", 100, 50, 20, "3");
        gm.addWanderer("Himmel", "himmel", "hero123", 150, 70, 15, "5");

        Wanderer himmel = (Wanderer) gm.getWanderers().get(1);
        himmel.completeQuest(5000, 0);

        ArrayList<User> ascending = gm.sortWandererByLevel(true);
        assertEquals("Frieren", ((Wanderer) ascending.get(0)).getName());
        assertEquals("Himmel", ((Wanderer) ascending.get(1)).getName());

        ArrayList<User> descending = gm.sortWandererByLevel(false);
        assertEquals("Himmel", ((Wanderer) descending.get(0)).getName());
        assertEquals("Frieren", ((Wanderer) descending.get(1)).getName());
    }

    @Test
    public void testAdvanceDayIncrementsDayResetsWandererHpAndDailyQuestStatus() throws DuplicateWandererException {
        gm.addWanderer("Frieren", "frieren", "himmel123", 100, 50, 20, "3");
        addThreeQuests();

        Wanderer frieren = firstWanderer();
        Quest dailyQuest = gm.fetchQuestbyId("Q1");
        Quest regularQuest = gm.fetchQuestbyId("Q2");

        frieren.takeDamage(40);
        dailyQuest.complete();
        regularQuest.complete();

        assertEquals(QuestStatus.TERSEDIA, dailyQuest.getStatus());
        assertEquals(QuestStatus.SELESAI, regularQuest.getStatus());

        gm.advanceDay();

        assertEquals(2, gm.getCurrentDay());
        assertEquals(frieren.getMaxHp(), frieren.getCurrentHp(), 0.001);
        assertEquals(QuestStatus.TERSEDIA, dailyQuest.getStatus());
        assertEquals(QuestStatus.SELESAI, regularQuest.getStatus());
    }

    @Test
    public void testIntegerAndDoubleValidationMethods() {
        assertTrue(gm.isNonNegativeInteger("0"));
        assertTrue(gm.isNonNegativeInteger("123"));
        assertFalse(gm.isNonNegativeInteger("-1"));
        assertFalse(gm.isNonNegativeInteger("1.5"));
        assertFalse(gm.isNonNegativeInteger(""));
        assertFalse(gm.isNonNegativeInteger(null));

        assertTrue(gm.isPositiveInteger("1"));
        assertFalse(gm.isPositiveInteger("0"));
        assertFalse(gm.isPositiveInteger("-1"));

        assertTrue(gm.isPositiveDouble("1"));
        assertTrue(gm.isPositiveDouble("1.5"));
        assertTrue(gm.isPositiveDouble(".5"));
        assertFalse(gm.isPositiveDouble("0"));
        assertFalse(gm.isPositiveDouble("-1"));
        assertFalse(gm.isPositiveDouble("abc"));
        assertFalse(gm.isPositiveDouble(""));
        assertFalse(gm.isPositiveDouble(null));
    }

    @Test
    public void testStringAndMenuValidationMethods() {
        assertTrue(gm.isAlphaNumericSpace("Goblin King 123"));
        assertFalse(gm.isAlphaNumericSpace("Goblin!"));
        assertFalse(gm.isAlphaNumericSpace(""));
        assertFalse(gm.isAlphaNumericSpace(null));

        assertTrue(gm.isNameInputValid("Goblin King"));
        assertFalse(gm.isNameInputValid("goblin King"));
        assertFalse(gm.isNameInputValid("Goblin king"));
        assertFalse(gm.isNameInputValid(""));
        assertFalse(gm.isNameInputValid(null));

        assertTrue(gm.isUsernameInputValid("user_123"));
        assertFalse(gm.isUsernameInputValid("user-name"));
        assertFalse(gm.isUsernameInputValid(""));
        assertFalse(gm.isUsernameInputValid(null));

        assertTrue(gm.isValidLoginInput("user_123", "pass"));
        assertFalse(gm.isValidLoginInput("user-name", "pass"));
        assertFalse(gm.isValidLoginInput("user_123", ""));

        assertTrue(gm.isJobClassValid("1"));
        assertTrue(gm.isJobClassValid("6"));
        assertFalse(gm.isJobClassValid("0"));
        assertFalse(gm.isJobClassValid("7"));

        assertTrue(gm.isQuestTypeValid("1"));
        assertTrue(gm.isQuestTypeValid("2"));
        assertTrue(gm.isQuestTypeValid("3"));
        assertFalse(gm.isQuestTypeValid("4"));

        assertTrue(gm.isQuestDifficultyValid("mudah"));
        assertTrue(gm.isQuestDifficultyValid("MENENGAH"));
        assertTrue(gm.isQuestDifficultyValid("Sulit"));
        assertFalse(gm.isQuestDifficultyValid("extreme"));
        assertFalse(gm.isQuestDifficultyValid(""));
        assertFalse(gm.isQuestDifficultyValid(null));

        assertTrue(gm.isSortOrderValid("asc"));
        assertTrue(gm.isSortOrderValid("DESC"));
        assertFalse(gm.isSortOrderValid("up"));
        assertFalse(gm.isSortOrderValid(""));
        assertFalse(gm.isSortOrderValid(null));
    }

    @Test
    public void testQuestIdAndLevelIntervalValidationMethods() {
        addThreeQuests();

        assertTrue(gm.isQuestIdFound("Q1"));
        assertFalse(gm.isQuestIdFound("Q999"));

        assertTrue(gm.isQuestIdValid("Q1"));
        assertFalse(gm.isQuestIdValid("Q999"));
        assertFalse(gm.isQuestIdValid("1"));
        assertFalse(gm.isQuestIdValid(null));

        assertTrue(gm.isLevelIntervalValid(1, 20));
        assertTrue(gm.isLevelIntervalValid(5, 5));
        assertFalse(gm.isLevelIntervalValid(10, 1));
        assertFalse(gm.isLevelIntervalValid(0, 20));
        assertFalse(gm.isLevelIntervalValid(1, 21));
    }

    @Test
    public void testShowMethodsReturnEmptyMessagesWhenDataDoesNotExist() {
        assertEquals("Belum ada pengembara terdaftar\n", gm.showWanderers(gm.getWanderers()));
        assertEquals("Belum ada quest terdaftar\n", gm.showQuests(gm.getQuests()));
        assertEquals("Belum ada monster terdaftar\n", gm.showMonsters());
    }

    @Test
    public void testShowMethodsReturnReadableContentWhenDataExists() throws DuplicateWandererException {
        gm.addWanderer("Frieren", "frieren", "himmel123", 100, 50, 20, "3");
        addThreeQuests();

        String wandererOutput = gm.showWanderers(gm.getWanderers());
        String monsterOutput = gm.showMonsters();
        String monsterNameOutput = gm.showMonstersName();
        String questOutput = gm.showQuests(gm.getQuests());
        String availableQuestOutput = gm.showAvailableQuest();

        assertTrue(wandererOutput.contains("Nama Pengembara: Frieren"));
        assertTrue(wandererOutput.contains("Username: frieren"));

        assertTrue(monsterOutput.contains("Nama Monster: Slime"));
        assertTrue(monsterOutput.contains("Nama Monster: Dragon"));
        assertTrue(monsterNameOutput.contains("1. Slime"));
        assertTrue(monsterNameOutput.contains("3. Dragon"));

        assertTrue(questOutput.contains("Latihan Harian"));
        assertTrue(questOutput.contains("Sarang Naga"));

        assertTrue(availableQuestOutput.contains("Q1. Latihan Harian"));
        assertTrue(availableQuestOutput.contains("Daily"));
        assertTrue(availableQuestOutput.contains(Difficulty.MUDAH.getDisplayName()));
    }
}