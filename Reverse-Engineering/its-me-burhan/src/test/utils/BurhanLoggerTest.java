package test.utils;

import utils.*;
import utils.enums.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import static org.junit.Assert.*;

public class BurhanLoggerTest {
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
    public void testSetFilePathShouldUseTemporaryFilePath() {
        assertEquals(logFile.getAbsolutePath(), BurhanLogger.getInstance().getFilePath());
    }

    @Test
    public void testSetFilePathWithNullShouldUseDefaultPath() {
        BurhanLogger.getInstance().setFilePath(null);

        assertEquals("guild_history.log", BurhanLogger.getInstance().getFilePath());
    }

    @Test
    public void testSetFilePathWithEmptyStringShouldUseDefaultPath() {
        BurhanLogger.getInstance().setFilePath("   ");

        assertEquals("guild_history.log", BurhanLogger.getInstance().getFilePath());
    }

    @Test
    public void testLogWritesOneLineWithExpectedFormat() throws IOException {
        BurhanLogger.getInstance().log(
            LogCategory.AUTH,
            "Admin: burhan",
            "Berhasil login ke dalam sistem."
        );

        List<String> lines = Files.readAllLines(logFile.toPath());

        assertEquals(1, lines.size());
        assertTrue(
            "Format log tidak sesuai: " + lines.get(0),
            lines.get(0).matches(
                "^\\[\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?Z\\] " +
                "\\[AUTH\\] \\[Admin: burhan\\] - Berhasil login ke dalam sistem\\.$"
            )
        );
    }

    @Test
    public void testLogAppendsInsteadOfOverwriting() throws IOException {
        BurhanLogger.getInstance().log(
            LogCategory.AUTH,
            "Admin: burhan",
            "Berhasil login ke dalam sistem."
        );

        BurhanLogger.getInstance().log(
            LogCategory.ADMIN,
            "Admin: burhan",
            "Monster 'Slime' (ID: M1) berhasil ditambahkan."
        );

        List<String> lines = Files.readAllLines(logFile.toPath());

        assertEquals(2, lines.size());
        assertTrue(lines.get(0).contains("[AUTH] [Admin: burhan] - Berhasil login ke dalam sistem."));
        assertTrue(lines.get(1).contains("[ADMIN] [Admin: burhan] - Monster 'Slime' (ID: M1) berhasil ditambahkan."));
    }

    @Test
    public void testLogWithExceptionWritesExceptionNameAndMessage() throws IOException {
        Exception exception = new IllegalArgumentException("Input tidak valid");

        BurhanLogger.getInstance().log(
            LogCategory.ERROR,
            "SYSTEM",
            "Terjadi error tak terduga.",
            exception
        );

        List<String> lines = Files.readAllLines(logFile.toPath());

        assertEquals(1, lines.size());
        assertTrue(lines.get(0).contains("[ERROR] [SYSTEM] - Terjadi error tak terduga."));
        assertTrue(lines.get(0).contains("Error: IllegalArgumentException: Input tidak valid"));
    }

    @Test
    public void testLogWithNullExceptionShouldBehaveLikeNormalLog() throws IOException {
        BurhanLogger.getInstance().log(
            LogCategory.ERROR,
            "SYSTEM",
            "Terjadi error tak terduga.",
            null
        );

        List<String> lines = Files.readAllLines(logFile.toPath());

        assertEquals(1, lines.size());
        assertTrue(lines.get(0).contains("[ERROR] [SYSTEM] - Terjadi error tak terduga."));
        assertFalse(lines.get(0).contains("Error:"));
    }

    @Test
    public void testLogWithExceptionWithoutMessageShouldWriteExceptionClassName() throws IOException {
        Exception exception = new IOException();

        BurhanLogger.getInstance().log(
            LogCategory.ERROR,
            "SYSTEM",
            "Gagal memproses data.",
            exception
        );

        List<String> lines = Files.readAllLines(logFile.toPath());

        assertEquals(1, lines.size());
        assertTrue(lines.get(0).contains("[ERROR] [SYSTEM] - Gagal memproses data."));
        assertTrue(lines.get(0).contains("Error: IOException"));
    }

    @Test
    public void testLoggerCreatesParentDirectoryIfMissing() throws IOException {
        File nestedLogFile = new File(tempFolder.getRoot(), "logs/nested/guild_history_test.log");
        assertFalse(nestedLogFile.exists());

        BurhanLogger.getInstance().setFilePath(nestedLogFile.getAbsolutePath());

        BurhanLogger.getInstance().log(
            LogCategory.SYSTEM,
            "SYSTEM",
            "Testing nested log file."
        );

        assertTrue(nestedLogFile.exists());

        List<String> lines = Files.readAllLines(nestedLogFile.toPath());

        assertEquals(1, lines.size());
        assertTrue(lines.get(0).contains("[SYSTEM] [SYSTEM] - Testing nested log file."));
    }

    @Test
    public void testActorFormatForWandererCanBeLogged() throws IOException {
        BurhanLogger.getInstance().log(
            LogCategory.AUTH,
            "Pengembara (MAGE): frieren",
            "Berhasil login ke dalam sistem."
        );

        List<String> lines = Files.readAllLines(logFile.toPath());

        assertEquals(1, lines.size());
        assertTrue(lines.get(0).contains("[AUTH] [Pengembara (MAGE): frieren] - Berhasil login ke dalam sistem."));
    }

    @Test
    public void testUniqueSkillMessageCanBeLogged() throws IOException {
        BurhanLogger.getInstance().log(
            LogCategory.BATTLE,
            "Pengembara (TANK): maple",
            "[TANK] Shield menyala! Damage Terpotong 50%"
        );

        List<String> lines = Files.readAllLines(logFile.toPath());

        assertEquals(1, lines.size());
        assertTrue(lines.get(0).contains("[BATTLE] [Pengembara (TANK): maple] - [TANK] Shield menyala! Damage Terpotong 50%"));
    }
}