package test;

import entities.*;
import entities.roles.*;
import utils.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.*;

public class ActorNameTest {
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private File logFile;

    @Before
    public void setUp() throws IOException {
        logFile = new File(tempFolder.getRoot(), "guild_history_test.log");
        BurhanLogger.getInstance().setFilePath(logFile.getAbsolutePath());
    }

    @After
    public void tearDown() {
        BurhanLogger.getInstance().setFilePath("guild_history.log");
    }

    @Test
    public void testAdminActorNameShouldMatchLogFormat() {
        Admin admin = new Admin();

        assertEquals("Admin: burhan", admin.getActorName());
    }

    @Test
    public void testMageActorNameShouldUseUppercaseJobName() {
        Mage mage = new Mage(1, "Frieren", "frieren", "123", 100, 50, 20);

        assertEquals("Pengembara (MAGE): frieren", mage.getActorName());
    }

    @Test
    public void testTankActorNameShouldUseUppercaseJobName() {
        Tank tank = new Tank(1, "Maple", "maple", "123", 100, 20, 10);

        assertEquals("Pengembara (TANK): maple", tank.getActorName());
    }

    @Test
    public void testAssassinActorNameShouldUseUppercaseJobName() {
        Assassin assassin = new Assassin(1, "Akatsuki", "akatsuki", "123", 100, 50, 20);

        assertEquals("Pengembara (ASSASSIN): akatsuki", assassin.getActorName());
    }

    @Test
    public void testFighterActorNameShouldUseUppercaseJobName() {
        Fighter fighter = new Fighter(1, "Stark", "stark", "123", 100, 50, 20);

        assertEquals("Pengembara (FIGHTER): stark", fighter.getActorName());
    }

    @Test
    public void testSupportActorNameShouldUseUppercaseJobName() {
        Support support = new Support(1, "Heiter", "heiter", "123", 100, 20, 20);

        assertEquals("Pengembara (SUPPORT): heiter", support.getActorName());
    }

    @Test
    public void testNoviceActorNameShouldUseNoviceJobName() {
        Wanderer wanderer = new Wanderer(1, "Charlie", "charlie", "123", 100, 20, 20);

        assertEquals("Pengembara (Novice): charlie", wanderer.getActorName());
    }

    @Test
    public void testGetActorNameShouldNotWriteLogFile() {
        Mage mage = new Mage(1, "Frieren", "frieren", "123", 100, 50, 20);

        mage.getActorName();

        assertFalse(
            "getActorName() tidak boleh menulis log. File log seharusnya belum dibuat.",
            logFile.exists()
        );
    }
}