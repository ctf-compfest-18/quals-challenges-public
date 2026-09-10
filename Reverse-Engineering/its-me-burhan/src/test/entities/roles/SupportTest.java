package test.entities.roles;

import entities.roles.*;
import test.LoggerTestHelper;

import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;

public class SupportTest {
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private File logFile;

    @Before
    public void setUp() throws IOException {
        logFile = LoggerTestHelper.isolateLogger(tempFolder);
    }

    @After
    public void tearDown() {
        LoggerTestHelper.resetLogger();
    }

    @Test
    public void testSupportHealsWhenNotFullHp() {
        Support support = new Support(1, "Sage", "sage", "pass", 100, 20, 5);

        support.setCurrentHp(50);
        support.onTurnStart();

        assertEquals(60.0, support.getCurrentHp(), 0.001);
        assertEquals("[SUPPORT]: Sage memulihkan 10.0 HP!", support.consumeCustomDamageNote());

        assertTrue(support.getPassiveSummary().contains("- Heal aktif: 1 kali"));
        assertTrue(support.getPassiveSummary().contains("- Total HP dipulihkan: 10"));
    }

    @Test
    public void testSupportDoesNotHealWhenFullHp() {
        Support support = new Support(1, "Sage", "sage", "pass", 100, 20, 5);

        support.onTurnStart();

        assertEquals(100.0, support.getCurrentHp(), 0.001);
        assertNull(support.consumeCustomDamageNote());

        assertTrue(support.getPassiveSummary().contains("- Heal aktif: 0 kali"));
        assertTrue(support.getPassiveSummary().contains("- Total HP dipulihkan: 0"));
    }

    @Test
    public void testSupportHealIsCappedAtMaxHp() {
        Support support = new Support(1, "Sage", "sage", "pass", 100, 20, 5);

        support.setCurrentHp(95);
        support.onTurnStart();

        assertEquals(100.0, support.getCurrentHp(), 0.001);
        assertEquals("[SUPPORT]: Sage memulihkan 5.0 HP!", support.consumeCustomDamageNote());

        assertTrue(support.getPassiveSummary().contains("- Heal aktif: 1 kali"));
        assertTrue(support.getPassiveSummary().contains("- Total HP dipulihkan: 5"));
    }

    @Test
    public void testResetBattleStateResetsSupportSummary() {
        Support support = new Support(1, "Sage", "sage", "pass", 100, 20, 5);

        support.setCurrentHp(50);
        support.onTurnStart();

        support.resetBattleState();

        assertTrue(support.getPassiveSummary().contains("- Heal aktif: 0 kali"));
        assertTrue(support.getPassiveSummary().contains("- Total HP dipulihkan: 0"));
        assertNull(support.consumeCustomDamageNote());
    }
}