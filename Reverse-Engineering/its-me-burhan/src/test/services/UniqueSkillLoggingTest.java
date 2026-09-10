package test.services;

import entities.monster.*;
import entities.roles.*;
import utils.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.*;

public class UniqueSkillLoggingTest {
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private File logFile;

    @Before
    public void setUp() throws IOException {
        logFile = tempFolder.newFile("guild_history_test.log");
        BurhanLogger.getInstance().setFilePath(logFile.getAbsolutePath());
    }

    @After
    public void tearDown() {
        BurhanLogger.getInstance().setFilePath("guild_history.log");
    }

    @Test
    public void testTankSkillShouldBeLoggedWhenShieldTriggers() throws IOException {
        Tank tank = new Tank(1, "Maple", "maple", "123", 100, 20, 10);
        tank.setCurrentHp(30);

        tank.modifyDamageTaken(80);

        assertLogContains("[BATTLE] [Pengembara (TANK): maple]");
        assertLogContains("[TANK]");
        assertLogContains("Shield");
    }

    @Test
    public void testMageSkillShouldBeLoggedWhenArcaneBurstTriggers() throws IOException {
        Mage mage = new Mage(1, "Frieren", "frieren", "123", 100, 50, 20);

        mage.onTurnEnd(0);
        mage.modifyDamageDealt(10);

        assertLogContains("[BATTLE] [Pengembara (MAGE): frieren]");
        assertLogContains("[MAGE]");
        assertLogContains("Arcane Burst");
    }

    @Test
    public void testAssassinSkillShouldBeLoggedWhenBleedIsApplied() throws IOException {
        Random alwaysTrigger = new Random() {
            @Override
            public double nextDouble() {
                return 0.0;
            }
        };

        Assassin assassin = new Assassin(1, "Akatsuki", "akatsuki", "123", 100, 50, 20, alwaysTrigger);
        Monster slime = new Monster(1, "Slime", 25, 5, 5, 10, 5);

        assassin.setBattleContext(slime, 1.0);
        assassin.modifyDamageDealt(30);

        assertLogContains("[BATTLE] [Pengembara (ASSASSIN): akatsuki]");
        assertLogContains("[ASSASSIN]");
        assertLogContains("Bleed");
        assertLogContains("Slime");
    }

    @Test
    public void testFighterSkillShouldBeLoggedWhenFuryStackChanges() throws IOException {
        Fighter fighter = new Fighter(1, "Stark", "stark", "123", 100, 50, 20);

        fighter.onTurnEnd(0);
        fighter.onTurnStart();

        assertLogContains("[BATTLE] [Pengembara (FIGHTER): stark] - [FIGHTER] Fury stacks aktif! (Total: 1)");
    }

    @Test
    public void testSupportSkillShouldBeLoggedWhenHealHappens() throws IOException {
        Support support = new Support(1, "Heiter", "heiter", "123", 100, 20, 20);
        support.setCurrentHp(50);

        support.onTurnStart();

        assertLogContains("[BATTLE] [Pengembara (SUPPORT): heiter]");
        assertLogContains("[SUPPORT]");
        assertLogContains("memulihkan");
    }

    private void assertLogContains(String expectedContent) throws IOException {
        List<String> lines = readLogLines();

        boolean found = false;
        for (String line : lines) {
            if (line.contains(expectedContent)) {
                found = true;
                break;
            }
        }

        assertTrue(
            "Log tidak mengandung: " + expectedContent + "\nIsi log:\n" + String.join("\n", lines),
            found
        );
    }

    private List<String> readLogLines() throws IOException {
        if (!logFile.exists()) {
            return Collections.emptyList();
        }
        return Files.readAllLines(logFile.toPath());
    }
}