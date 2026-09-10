package test.services;

import entities.User;
import entities.Wanderer;
import entities.roles.Mage;
import exception.DuplicateWandererException;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import services.AuthService;
import services.WandererService;
import test.LoggerTestHelper;

import java.io.IOException;
import java.util.ArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class WandererServiceTest {
    private AuthService authService;
    private WandererService wandererService;

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Before
    public void setUp() throws IOException {
        LoggerTestHelper.isolateLogger(tempFolder);
        authService = new AuthService();
        wandererService = new WandererService(authService);
    }

    @After
    public void tearDown() {
        LoggerTestHelper.resetLogger();
    }

    @Test
    public void testAddWandererStoresWandererAndRegistersLoginUser() throws DuplicateWandererException {
        wandererService.addWanderer("Frieren", "frieren", "staff123", 100, 50, 20, "3");

        ArrayList<User> wanderers = wandererService.getWanderers();
        assertEquals(1, wandererService.getWanderersCount());
        assertTrue(wanderers.get(0) instanceof Mage);
        assertTrue(authService.isUsernameTaken("frieren"));
        assertEquals("P1", ((Wanderer) wanderers.get(0)).getId());
    }

    @Test
    public void testDuplicateUsernameThrowsDuplicateWandererException() throws DuplicateWandererException {
        wandererService.addWanderer("Frieren", "frieren", "staff123", 100, 50, 20, "3");

        try {
            wandererService.addWanderer("Fern", "frieren", "magic123", 100, 50, 20, "3");
            fail("addWanderer seharusnya melempar DuplicateWandererException.");
        } catch (DuplicateWandererException e) {
            assertTrue(e.getMessage().contains("frieren"));
        }
    }

    @Test
    public void testReturnedWandererListIsCopy() throws DuplicateWandererException {
        wandererService.addWanderer("Frieren", "frieren", "staff123", 100, 50, 20, "3");

        ArrayList<User> wanderers = wandererService.getWanderers();
        wanderers.clear();

        assertEquals(1, wandererService.getWanderersCount());
    }

    @Test
    public void testJobStringAndIntervalValidation() {
        assertEquals("NOVICE", wandererService.addWandererJobString("1"));
        assertEquals("SUPPORT", wandererService.addWandererJobString("6"));
        assertTrue(wandererService.isJobClassValid("5"));
        assertFalse(wandererService.isJobClassValid("7"));
        assertTrue(wandererService.isLevelIntervalValid(1, 20));
        assertFalse(wandererService.isLevelIntervalValid(10, 1));
    }

    @Test
    public void testValidateWandererInputAllowsValidInput() {
        wandererService.validateWandererInput("Frieren", "frieren", "staff123", "100", "50.5", ".25", "3");
    }

    @Test
    public void testValidateWandererInputRejectsInvalidName() {
        assertInvalidWandererInput(
                "frieren", "frieren", "staff123", "100", "50", "20", "3",
                "Nama pengembara hanya boleh berisi karakter alfanumerik dan spasi, huruf pertama setiap kata harus kapital, dan selain itu tidak boleh ada huruf kapital."
        );
    }

    @Test
    public void testValidateWandererInputRejectsInvalidUsername() {
        assertInvalidWandererInput(
                "Frieren", "frieren-sama", "staff123", "100", "50", "20", "3",
                "Username hanya boleh berisi huruf, angka, dan underscore (_)."
        );
    }

    @Test
    public void testValidateWandererInputRejectsDuplicateUsername() throws DuplicateWandererException {
        wandererService.addWanderer("Frieren", "frieren", "staff123", 100, 50, 20, "3");

        assertInvalidWandererInput(
                "Fern", "frieren", "magic123", "100", "50", "20", "3",
                "Username sudah dipakai oleh user lain, harap masukkan username lain."
        );
    }

    @Test
    public void testValidateWandererInputRejectsEmptyPassword() {
        assertInvalidWandererInput(
                "Frieren", "frieren", "", "100", "50", "20", "3",
                "Password tidak boleh kosong."
        );
    }

    @Test
    public void testValidateWandererInputRejectsInvalidMaxHp() {
        assertInvalidWandererInput(
                "Frieren", "frieren", "staff123", "0", "50", "20", "3",
                "HP maksimal harus bilangan positif."
        );
    }

    @Test
    public void testValidateWandererInputRejectsInvalidAttackPower() {
        assertInvalidWandererInput(
                "Frieren", "frieren", "staff123", "100", "-50", "20", "3",
                "Attack power harus bilangan positif."
        );
    }

    @Test
    public void testValidateWandererInputRejectsInvalidDefense() {
        assertInvalidWandererInput(
                "Frieren", "frieren", "staff123", "100", "50", "abc", "3",
                "Defense harus bilangan positif."
        );
    }

    @Test
    public void testValidateWandererInputRejectsInvalidJob() {
        assertInvalidWandererInput(
                "Frieren", "frieren", "staff123", "100", "50", "20", "9",
                "Job class harus salah satu dari yang tertera pada list."
        );
    }

    @Test
    public void testValidateLevelIntervalInputAllowsValidInterval() {
        wandererService.validateLevelIntervalInput("1", "20");
        wandererService.validateLevelIntervalInput("5", "5");
    }

    @Test
    public void testValidateLevelIntervalInputRejectsNonPositiveOrNonIntegerInput() {
        assertInvalidLevelIntervalInput("0", "5", "Pilihan tidak valid. Harap masukkan pilihan dengan benar.");
        assertInvalidLevelIntervalInput("abc", "5", "Pilihan tidak valid. Harap masukkan pilihan dengan benar.");
        assertInvalidLevelIntervalInput("5", "", "Pilihan tidak valid. Harap masukkan pilihan dengan benar.");
    }

    @Test
    public void testValidateLevelIntervalInputRejectsOutOfRangeOrReversedInterval() {
        assertInvalidLevelIntervalInput("10", "1", "Input tidak valid. Harap masukkan bilangan bulat di antara 1 dan 20 dengan batas bawah tidak melebihi batas atas.");
        assertInvalidLevelIntervalInput("1", "21", "Input tidak valid. Harap masukkan bilangan bulat di antara 1 dan 20 dengan batas bawah tidak melebihi batas atas.");
    }

    @Test
    public void testValidateWandererSortTypeAllowsKnownSortTypes() {
        wandererService.validateWandererSortType("1");
        wandererService.validateWandererSortType("2");
    }

    @Test
    public void testValidateWandererSortTypeRejectsInvalidInput() {
        assertInvalidWandererSortType(null);
        assertInvalidWandererSortType("");
        assertInvalidWandererSortType("3");
        assertInvalidWandererSortType("name");
    }


    @Test
    public void testHandlePostBattleLoseStreakDemotesLevelAboveOne() throws DuplicateWandererException {
        wandererService.addWanderer("Charlie", "charlie", "charlie123", 50, 5, 5, "1");
        Wanderer wanderer = (Wanderer) wandererService.getWanderers().get(0);
        wanderer.completeQuest(5000, 0);

        wanderer.recordBattleResult(false);
        wanderer.recordBattleResult(false);
        wanderer.recordBattleResult(false);

        boolean eliminated = wandererService.handlePostBattleLoseStreak(wanderer);

        assertFalse(eliminated);
        assertEquals(1, wanderer.getLevel());
        assertEquals(0, wanderer.getExp());
        assertEquals(0, wanderer.getConsecutiveLosses());
        assertEquals(1, wandererService.getWanderersCount());
        assertTrue(authService.isUsernameTaken("charlie"));
    }

    @Test
    public void testHandlePostBattleLoseStreakEliminatesLevelOneWanderer() throws DuplicateWandererException {
        wandererService.addWanderer("Charlie", "charlie", "charlie123", 50, 5, 5, "1");
        Wanderer wanderer = (Wanderer) wandererService.getWanderers().get(0);

        wanderer.recordBattleResult(false);
        wanderer.recordBattleResult(false);
        wanderer.recordBattleResult(false);

        boolean eliminated = wandererService.handlePostBattleLoseStreak(wanderer);

        assertTrue(eliminated);
        assertTrue(wanderer.isEliminated());
        assertEquals(0, wandererService.getWanderersCount());
        assertFalse(authService.isUsernameTaken("charlie"));
        assertNull(authService.login("charlie", "charlie123"));
    }

    @Test
    public void testHandlePostBattleLoseStreakDoesNothingBeforeThreeLosses() throws DuplicateWandererException {
        wandererService.addWanderer("Charlie", "charlie", "charlie123", 50, 5, 5, "1");
        Wanderer wanderer = (Wanderer) wandererService.getWanderers().get(0);

        wanderer.recordBattleResult(false);
        wanderer.recordBattleResult(false);

        boolean eliminated = wandererService.handlePostBattleLoseStreak(wanderer);

        assertFalse(eliminated);
        assertEquals(1, wandererService.getWanderersCount());
        assertEquals(2, wanderer.getConsecutiveLosses());
    }

    @Test
    public void testNextWandererIdStillIncreasesAfterRemovingEarlierWanderer() throws DuplicateWandererException {
        wandererService.addWanderer("Charlie", "charlie", "charlie123", 50, 5, 5, "1");
        wandererService.addWanderer("Diana", "diana", "diana123", 50, 5, 5, "1");
        Wanderer charlie = (Wanderer) wandererService.getWanderers().get(0);

        wandererService.removeWanderer(charlie);
        wandererService.addWanderer("Evan", "evan", "evan123", 50, 5, 5, "1");

        Wanderer evan = (Wanderer) wandererService.getWanderers().get(1);
        assertEquals("P3", evan.getId());
    }

    @Test
    public void testNextWandererIdDoesNotReuseIdAfterOnlyWandererRemoved() throws DuplicateWandererException {
        wandererService.addWanderer("Charlie", "charlie", "charlie123", 50, 5, 5, "1");
        Wanderer charlie = (Wanderer) wandererService.getWanderers().get(0);

        wandererService.removeWanderer(charlie);
        wandererService.addWanderer("Diana", "diana", "diana123", 50, 5, 5, "1");

        Wanderer diana = (Wanderer) wandererService.getWanderers().get(0);
        assertEquals("P2", diana.getId());
    }

    @Test
    public void testNextWandererIdDoesNotReuseIdAfterLoseStreakElimination() throws DuplicateWandererException {
        wandererService.addWanderer("Charlie", "charlie", "charlie123", 50, 5, 5, "1");
        Wanderer charlie = (Wanderer) wandererService.getWanderers().get(0);

        charlie.recordBattleResult(false);
        charlie.recordBattleResult(false);
        charlie.recordBattleResult(false);
        assertTrue(wandererService.handlePostBattleLoseStreak(charlie));

        wandererService.addWanderer("Diana", "diana", "diana123", 50, 5, 5, "1");

        Wanderer diana = (Wanderer) wandererService.getWanderers().get(0);
        assertEquals("P2", diana.getId());
        assertNull(authService.login("charlie", "charlie123"));
    }

    private void assertInvalidWandererInput(
            String name,
            String username,
            String password,
            String maxHp,
            String attackPower,
            String defense,
            String job,
            String expectedMessage
    ) {
        try {
            wandererService.validateWandererInput(name, username, password, maxHp, attackPower, defense, job);
            fail("validateWandererInput seharusnya melempar IllegalArgumentException.");
        } catch (IllegalArgumentException e) {
            assertEquals(expectedMessage, e.getMessage());
        }
    }

    private void assertInvalidLevelIntervalInput(String lowerBound, String upperBound, String expectedMessage) {
        try {
            wandererService.validateLevelIntervalInput(lowerBound, upperBound);
            fail("validateLevelIntervalInput seharusnya melempar IllegalArgumentException.");
        } catch (IllegalArgumentException e) {
            assertEquals(expectedMessage, e.getMessage());
        }
    }

    private void assertInvalidWandererSortType(String sortType) {
        try {
            wandererService.validateWandererSortType(sortType);
            fail("validateWandererSortType seharusnya melempar IllegalArgumentException.");
        } catch (IllegalArgumentException e) {
            assertEquals("Urutan tidak valid. Harap masukkan urutan dengan benar.", e.getMessage());
        }
    }
}
