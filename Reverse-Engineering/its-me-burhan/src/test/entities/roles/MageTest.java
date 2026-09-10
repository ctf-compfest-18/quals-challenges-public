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

public class MageTest {
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
    public void testMageBurstIgnoresBaseDamageAndUsesTwoTimesAttack() {
        Mage mage = new Mage(1, "Mage", "mage", "pass", 100, 30, 5);

        mage.onTurnStart();
        mage.modifyDamageDealt(10);
        mage.onTurnEnd(10);

        mage.onTurnStart();
        double damage = mage.modifyDamageDealt(999);

        assertEquals(60.0, damage, 0.001);
        assertEquals("[MAGE] Arcane Burst aktif!", mage.consumeCustomDamageNote());
    }

    @Test
    public void testMagePassiveSummary() {
        Mage mage = new Mage(1, "Mage", "mage", "pass", 100, 30, 5);

        mage.onTurnStart();
        mage.modifyDamageDealt(10);
        mage.onTurnEnd(10);

        mage.onTurnStart();
        mage.modifyDamageDealt(10);

        String summary = mage.getPassiveSummary();

        assertTrue(summary.contains("Overcharged terkumpul: 1 kali"));
        assertTrue(summary.contains("Arcane Burst aktif: 1 kali"));
    }

    @Test
    public void testResetBattleStateResetsMageSummary() {
        Mage mage = new Mage(1, "Mage", "mage", "pass", 100, 30, 5);

        mage.onTurnEnd(10);
        mage.resetBattleState();

        String summary = mage.getPassiveSummary();

        assertTrue(summary.contains("Overcharged terkumpul: 0 kali"));
        assertTrue(summary.contains("Arcane Burst aktif: 0 kali"));
    }
}