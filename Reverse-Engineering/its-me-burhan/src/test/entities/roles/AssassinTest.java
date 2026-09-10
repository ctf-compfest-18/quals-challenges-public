package test.entities.roles;

import entities.monster.*;
import entities.roles.*;
import test.LoggerTestHelper;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.util.Random;

public class AssassinTest {
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

    private static class AlwaysBleedRandom extends Random {
        @Override
        public double nextDouble() {
            return 0.1; // always < 0.5
        }
    }

    private static class NeverBleedRandom extends Random {
        @Override
        public double nextDouble() {
            return 0.9; // always >= 0.5
        }
    }

    @Test
    public void testModifyDamageDealtAlwaysReturnsBaseDamage() {
        Assassin assassin = new Assassin(1, "Assassin", "assassin", "pass", 100, 50, 5, new AlwaysBleedRandom());
        Monster monster = new Monster(1, "Slime", 100, 10, 2, 50, 10);

        assassin.setBattleContext(monster, 1.0);

        assertEquals(30.0, assassin.modifyDamageDealt(30.0), 0.001);
    }

    @Test
    public void testBleedTriggeredWhenRandomBelowHalf() {
        Assassin assassin = new Assassin(1, "Assassin", "assassin", "pass", 100, 50, 5, new AlwaysBleedRandom());
        Monster monster = new Monster(1, "Slime", 100, 10, 2, 50, 10);

        assassin.setBattleContext(monster, 1.0);
        assassin.modifyDamageDealt(30.0);

        assertTrue(assassin.getPassiveSummary().contains("Bleed berhasil diterapkan: 1 kali"));

        monster.onTurnStart();

        assertEquals(90.0, monster.getCurrentHp(), 0.001);
        assertEquals(1, monster.getBleedTriggeredCount());
    }

    @Test
    public void testBleedNotTriggeredWhenRandomAboveHalf() {
        Assassin assassin = new Assassin(1, "Assassin", "assassin", "pass", 100, 50, 5, new NeverBleedRandom());
        Monster monster = new Monster(1, "Slime", 100, 10, 2, 50, 10);

        assassin.setBattleContext(monster, 1.0);
        assassin.modifyDamageDealt(30.0);

        assertTrue(assassin.getPassiveSummary().contains("Bleed berhasil diterapkan: 0 kali"));

        monster.onTurnStart();

        assertEquals(100.0, monster.getCurrentHp(), 0.001);
        assertEquals(0, monster.getBleedTriggeredCount());
    }

    @Test
    public void testModifyDamageDealtWithoutTargetDoesNotCrash() {
        Assassin assassin = new Assassin(1, "Assassin", "assassin", "pass", 100, 50, 5, new AlwaysBleedRandom());

        assertEquals(30.0, assassin.modifyDamageDealt(30.0), 0.001);
        assertTrue(assassin.getPassiveSummary().contains("Bleed berhasil diterapkan: 0 kali"));
    }

    @Test
    public void testResetBattleStateResetsSummary() {
        Assassin assassin = new Assassin(1, "Assassin", "assassin", "pass", 100, 50, 5, new AlwaysBleedRandom());
        Monster monster = new Monster(1, "Slime", 100, 10, 2, 50, 10);

        assassin.setBattleContext(monster, 1.0);
        assassin.modifyDamageDealt(30.0);

        assassin.resetBattleState();

        assertTrue(assassin.getPassiveSummary().contains("Bleed berhasil diterapkan: 0 kali"));
    }
}