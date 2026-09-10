package test.utils;

import exception.*;
import quests.enums.*;
import services.*;
import utils.*;
import utils.enums.*;

import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ReportGeneratorTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private GameManager createGameManagerWithMonster() {
        GameManager gm = new GameManager();
        gm.addMonster("Ignis Drake", 300, 200, 20, 1000, 500);
        return gm;
    }

    private GameManager createGameManagerWithQuest() {
        GameManager gm = createGameManagerWithMonster();
        gm.addQuest(
                "Sarang Naga",
                "Kalahkan naga tua",
                Difficulty.fromString("sulit").orElse(Difficulty.SULIT),
                gm.fetchMonsterbyId("1"),
                "3",
                "10000",
                "5000"
        );
        return gm;
    }

    private File createFile(String fileName, String content) throws Exception {
        File file = new File(temporaryFolder.getRoot(), fileName);
        PrintWriter writer = new PrintWriter(file);
        writer.print(content);
        writer.close();
        return file;
    }

    private String readFile(File file) throws Exception {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    @Test
    public void testPreviewExportQuestPrintsTable() throws Exception {
        GameManager gm = createGameManagerWithQuest();
        ReportGenerator generator = new ReportGenerator(gm);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;

        try {
            System.setOut(new PrintStream(output));
            generator.previewExport(AdminIOCsvMode.QUEST);
        } finally {
            System.setOut(originalOut);
        }

        String text = output.toString();
        String lowerText = text.toLowerCase();

        assertTrue(text.contains("Data yang akan diekspor"));
        assertTrue(text.contains("ID Quest"));
        assertTrue(text.contains("Nama Quest"));
        assertTrue(text.contains("Deskripsi"));
        assertTrue(text.contains("Kesulitan"));
        assertTrue(text.contains("Tipe"));
        assertTrue(text.contains("Monster"));
        assertTrue(text.contains("Min. Lv"));
        assertTrue(text.contains("Status"));

        assertTrue(text.contains("Sarang Naga"));
        assertTrue(text.contains("Ignis Drake"));
        assertTrue(lowerText.contains("bounty"));
        assertTrue(lowerText.contains("tersedia"));
    }

    @Test
    public void testPreviewExportWandererPrintsTable() throws Exception {
        GameManager gm = new GameManager();
        gm.addWanderer("Frieren", "frieren", "himmel123", 100, 50, 20, "3");

        ReportGenerator generator = new ReportGenerator(gm);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;

        try {
            System.setOut(new PrintStream(output));
            generator.previewExport(AdminIOCsvMode.WANDERER);
        } finally {
            System.setOut(originalOut);
        }

        String text = output.toString();

        assertTrue(text.contains("Data yang akan diekspor"));

        assertTrue(text.contains("ID"));
        assertTrue(text.contains("Nama"));
        assertTrue(text.contains("Username"));
        assertTrue(text.contains("Level"));
        assertTrue(text.contains("Exp"));
        assertTrue(text.contains("Koin"));
        assertTrue(text.contains("HP Saat Ini"));
        assertTrue(text.contains("HP Maksimal"));
        assertTrue(text.contains("Attack"));
        assertTrue(text.contains("Defense"));

        assertTrue(text.contains("Frieren"));
        assertTrue(text.contains("frieren"));
        assertTrue(text.contains("100"));
        assertTrue(text.contains("50"));
        assertTrue(text.contains("20"));

        // Password tidak boleh muncul di export preview admin
        assertFalse(text.contains("himmel123"));
    }

    @Test
    public void testExportToCsvQuestCreatesSnakeCaseHeaderAndData() throws Exception {
        GameManager gm = createGameManagerWithQuest();
        ReportGenerator generator = new ReportGenerator(gm);
        File file = new File(temporaryFolder.getRoot(), "quests_export.csv");

        generator.exportToCsv(file.getAbsolutePath(), AdminIOCsvMode.QUEST);

        String content = readFile(file).replace("\r\n", "\n");
        String header = content.split("\n")[0];

        assertTrue(header.contains("id"));
        assertTrue(header.contains("name"));
        assertTrue(header.contains("description"));
        assertTrue(header.contains("difficulty"));
        assertTrue(header.contains("type"));
        assertTrue(header.contains("monster_id"));
        assertTrue(header.contains("monster_name"));
        assertTrue(header.contains("exp_reward"));
        assertTrue(header.contains("coin_reward"));
        assertTrue(header.contains("bonus_exp"));
        assertTrue(header.contains("bonus_coin"));
        assertTrue(header.contains("minimum_level"));
        assertTrue(header.contains("status"));

        assertTrue(content.contains("Sarang Naga"));
        assertTrue(content.contains("sulit"));
        assertTrue(content.contains("bounty"));
        assertTrue(content.contains("Ignis Drake"));
        assertTrue(content.contains("10000"));
        assertTrue(content.contains("5000"));
        assertTrue(content.toLowerCase().contains("tersedia"));
    }

    @Test
    public void testExportToTxtWandererCreatesReadableReport() throws Exception {
        GameManager gm = new GameManager();
        gm.addWanderer("Frieren", "frieren", "himmel123", 100, 50, 20, "3");

        ReportGenerator generator = new ReportGenerator(gm);
        File file = new File(temporaryFolder.getRoot(), "wanderers_export.txt");

        generator.exportToTxt(file.getAbsolutePath(), AdminIOCsvMode.WANDERER);

        String content = readFile(file);
        String lowerContent = content.toLowerCase();

        assertTrue(content.contains("Report Pengembara BurhanQuest"));
        assertTrue(content.contains("Total pengembara: 1"));

        assertTrue(content.contains("ID"));
        assertTrue(content.contains("Nama"));
        assertTrue(content.contains("Username"));
        assertTrue(content.contains("HP"));
        assertTrue(content.contains("ATK"));
        assertTrue(content.contains("DEF"));

        assertTrue(content.contains("Frieren"));
        assertTrue(content.contains("frieren"));
        assertTrue(lowerContent.contains("mage"));
        assertTrue(content.contains("100/100"));
        assertTrue(content.contains("50"));
        assertTrue(content.contains("20"));

        // Password tidak boleh ikut ke file export
        assertFalse(content.contains("himmel123"));
    }

    @Test
    public void testImportFromCsvQuestPrintsPreviewThenAddsQuest() throws Exception {
        GameManager gm = createGameManagerWithMonster();
        ReportGenerator generator = new ReportGenerator(gm);
        File file = createFile(
                "quests_import_true.csv",
                "nama,description,difficulty,monster,type,bonus_exp,bonus_coin\n" +
                "Latihan Harian,Harian,mudah,M1,daily,0,0\n"
        );
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;

        try {
            System.setOut(new PrintStream(output));
            generator.importFromCsv(file.getAbsolutePath(), AdminIOCsvMode.QUEST);
        } finally {
            System.setOut(originalOut);
        }

        String text = output.toString();
        assertEquals(1, gm.getQuestsCount());
        assertTrue(text.contains("Data yang akan disimpan"));
        assertTrue(text.contains("Latihan Harian"));
    }

    @Test
    public void testImportFromCsvWandererPrintsPreviewThenAddsWanderer() throws Exception {
        GameManager gm = new GameManager();
        ReportGenerator generator = new ReportGenerator(gm);
        File file = createFile(
                "wanderers_import_true.csv",
                "name,username,password,max_hp,attack,defense,job\n" +
                "Himmel,himmel,hero123,150,70,15,Fighter\n"
        );
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;

        try {
            System.setOut(new PrintStream(output));
            generator.importFromCsv(file.getAbsolutePath(), AdminIOCsvMode.WANDERER);
        } finally {
            System.setOut(originalOut);
        }

        String text = output.toString();
        assertEquals(1, gm.getWanderersCount());
        assertTrue(text.contains("Data yang akan disimpan"));
        assertTrue(text.contains("Himmel"));
    }

    @Test
    public void testImportFromCsvWandererDuplicateStillPrintsPreviewBeforeException() throws Exception {
        GameManager gm = new GameManager();
        gm.addWanderer("Frieren", "frieren", "himmel123", 100, 50, 20, "3");
        ReportGenerator generator = new ReportGenerator(gm);
        File file = createFile(
                "wanderers_import_duplicate.csv",
                "name,username,password,max_hp,attack,defense,job\n" +
                "Frieren Dua,frieren,pass123,100,50,20,Mage\n"
        );
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;

        try {
            System.setOut(new PrintStream(output));
            generator.importFromCsv(file.getAbsolutePath(), AdminIOCsvMode.WANDERER);
            fail("Seharusnya melempar BurhanQuestException karena username duplicate.");
        } catch (BurhanQuestException e) {
            String message = e.getMessage().toLowerCase();
            assertTrue(message.contains("duplik") || message.contains("sudah digunakan"));
            assertTrue(output.toString().contains("Data yang akan disimpan"));
            assertTrue(output.toString().contains("Frieren Dua"));
        } finally {
            System.setOut(originalOut);
        }
    }

    @Test
    public void testImportFromCsvThrowsForInvalidFileType() throws Exception {
        GameManager gm = new GameManager();
        ReportGenerator generator = new ReportGenerator(gm);

        try {
            generator.importFromCsv("salah.txt", AdminIOCsvMode.QUEST);
            fail("Seharusnya melempar InvalidFileTypeException untuk file non-csv.");
        } catch (InvalidFileTypeException e) {
            assertEquals("kesalahan nama tipe file", e.getMessage());
        }
    }
}