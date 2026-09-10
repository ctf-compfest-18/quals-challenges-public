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

public class FighterTest {
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
    public void testFirstAttackHasNoFuryBonus() {
        Fighter fighter = new Fighter(1, "Guinevere", "fighter", "pass", 125, 45, 5);

        fighter.onTurnStart();

        assertNull(fighter.consumeCustomDamageNote());

        double damage = fighter.modifyDamageDealt(31.25);

        assertEquals(31.25, damage, 0.001);

        fighter.onTurnEnd(damage);

        assertEquals("[FIGHTER] Stacks bertambah!", fighter.consumeCustomDamageNote());
    }

    @Test
    public void testFuryStackOneAddsTenPercentAttack() {
        Fighter fighter = new Fighter(1, "Guinevere", "fighter", "pass", 125, 45, 5);

        fighter.onTurnEnd(31.25);

        fighter.onTurnStart();

        assertEquals(
            "[FIGHTER]: Fury Stacks (I) aktif, Damage bertambah sebesar 10% ATK!",
            fighter.consumeCustomDamageNote()
        );

        double damage = fighter.modifyDamageDealt(31.25);

        assertEquals(35.75, damage, 0.001);
        assertTrue(fighter.getPassiveSummary().contains("Fury Stacks I aktif: 1 kali"));
    }

    @Test
    public void testFuryStackTwoAddsTwentyPercentAttack() {
        Fighter fighter = new Fighter(1, "Guinevere", "fighter", "pass", 125, 45, 5);

        fighter.onTurnEnd(0);
        fighter.onTurnEnd(0);

        fighter.onTurnStart();

        assertEquals(
            "[FIGHTER]: Fury Stacks (II) aktif, Damage bertambah sebesar 20% ATK!",
            fighter.consumeCustomDamageNote()
        );

        double damage = fighter.modifyDamageDealt(31.25);

        assertEquals(40.25, damage, 0.001);
        assertTrue(fighter.getPassiveSummary().contains("Fury Stacks II aktif: 1 kali"));
    }

    @Test
    public void testFuryStackThreeAddsThirtyPercentAttack() {
        Fighter fighter = new Fighter(1, "Guinevere", "fighter", "pass", 125, 45, 5);

        fighter.onTurnEnd(0);
        fighter.onTurnEnd(0);
        fighter.onTurnEnd(0);

        fighter.onTurnStart();

        assertEquals(
            "[FIGHTER]: Fury Stacks (III) aktif, Damage bertambah sebesar 30% ATK!",
            fighter.consumeCustomDamageNote()
        );

        double damage = fighter.modifyDamageDealt(31.25);

        assertEquals(44.75, damage, 0.001);
        assertTrue(fighter.getPassiveSummary().contains("Fury Stacks III aktif: 1 kali"));
    }

    @Test
    public void testFuryStackResetsAfterThreeStacks() {
        Fighter fighter = new Fighter(1, "Guinevere", "fighter", "pass", 125, 45, 5);

        fighter.onTurnEnd(0);
        fighter.onTurnEnd(0);
        fighter.onTurnEnd(0);
        fighter.onTurnEnd(0);

        assertEquals("[FIGHTER] Stacks full, Reset ke 0!", fighter.consumeCustomDamageNote());
        assertTrue(fighter.getPassiveSummary().contains("Reset Fury Stack: 1 kali"));
    }

    @Test
    public void testResetBattleStateResetsFighterSummary() {
        Fighter fighter = new Fighter(1, "Guinevere", "fighter", "pass", 125, 45, 5);

        fighter.onTurnEnd(0);
        fighter.onTurnStart();
        fighter.modifyDamageDealt(31.25);

        fighter.resetBattleState();

        String summary = fighter.getPassiveSummary();

        assertTrue(summary.contains("Fury Stacks I aktif: 0 kali"));
        assertTrue(summary.contains("Fury Stacks II aktif: 0 kali"));
        assertTrue(summary.contains("Fury Stacks III aktif: 0 kali"));
        assertTrue(summary.contains("Reset Fury Stack: 0 kali"));
    }
}