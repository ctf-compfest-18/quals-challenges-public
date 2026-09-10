package test.services;

import entities.*;
import exception.*;
import quests.enums.*;
import services.*;
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

import static org.junit.Assert.*;

public class GameManagerLoggingTest {
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private File logFile;
    private GameManager gm;

    @Before
    public void setUp() throws IOException {
        logFile = tempFolder.newFile("guild_history_test.log");
        BurhanLogger.getInstance().setFilePath(logFile.getAbsolutePath());
        gm = new GameManager();
    }

    @After
    public void tearDown() {
        BurhanLogger.getInstance().setFilePath("guild_history.log");
    }

    @Test
    public void testLoginReturnsAdmin() {
        User user = gm.login("burhan", "burunghantu123");

        assertNotNull(user);
        assertTrue(user instanceof Admin);
        assertEquals("Admin: burhan", user.getActorName());
    }

    @Test
    public void testFailedLoginReturnsNull() {
        User user = gm.login("burhan", "password_salah");

        assertNull(user);
    }

    @Test
    public void testAddMonsterShouldAddMonsterData() {
        gm.addMonster("Slime", 25, 5, 5, 10, 5);

        assertEquals(1, gm.getMonstersCount());
        assertEquals("M1", gm.getMonsters().get(0).getMonsterId());
        assertEquals("Slime", gm.getMonsters().get(0).getName());
    }

    @Test
    public void testAddQuestShouldAddQuestData() {
        gm.addMonster("Slime", 25, 5, 5, 10, 5);

        gm.addQuest(
            "Memburu Slime",
            "Memburu Slime Liar",
            Difficulty.MUDAH,
            gm.fetchMonsterbyId("1"),
            "1",
            null,
            null
        );

        assertEquals(1, gm.getQuestsCount());
        assertEquals("Q1", gm.getQuests().get(0).getId());
        assertEquals("Memburu Slime", gm.getQuests().get(0).getName());
    }

    @Test
    public void testAddWandererShouldAddWandererData() throws DuplicateWandererException {
        gm.addWanderer("Frieren", "frieren", "123", 100, 50, 20, "3");

        assertEquals(1, gm.getWanderersCount());

        Wanderer wanderer = (Wanderer) gm.getWanderers().get(0);

        assertEquals("P1", wanderer.getId());
        assertEquals("Frieren", wanderer.getName());
        assertEquals("Pengembara (MAGE): frieren", wanderer.getActorName());
    }

    @Test
    public void testAdvanceDayShouldIncreaseCurrentDay() {
        gm.advanceDay();

        assertEquals(2, gm.getCurrentDay());
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