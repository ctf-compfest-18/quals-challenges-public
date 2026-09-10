package test.utils;

import entities.*;
import exception.*;
import services.*;
import utils.*;
import utils.enums.*;

import static org.junit.Assert.*;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class WandererExporterTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private GameManager createGameManagerWithWanderer() throws Exception {
        GameManager gameManager = new GameManager();

        gameManager.addWanderer(
            "Frieren",
            "frieren",
            "himmel123",
            100,
            50,
            20,
            "3"
        );

        return gameManager;
    }

    private Wanderer getFirstWanderer(GameManager gameManager) {
        return (Wanderer) gameManager.getWanderers().get(0);
    }

    @Test
    public void testBuildReportTextContainsWandererData() throws Exception {
        GameManager gameManager = createGameManagerWithWanderer();
        Wanderer wanderer = getFirstWanderer(gameManager);

        WandererExporter exporter = new WandererExporter(gameManager);
        String report = exporter.buildReportText(wanderer);

        assertTrue(report.contains("LAPORAN DATA PENGEMBARA"));
        assertTrue(report.contains("ID Pengembara"));
        assertTrue(report.contains("P1"));
        assertTrue(report.contains("Nama"));
        assertTrue(report.contains("Frieren"));
        assertTrue(report.contains("Username"));
        assertTrue(report.contains("frieren"));
        assertTrue(report.contains("Job"));
        assertTrue(report.contains("Mage"));
        assertTrue(report.contains("HP"));
        assertTrue(report.contains("100/100"));
        assertTrue(report.contains("Attack"));
        assertTrue(report.contains("50"));
        assertTrue(report.contains("Defense"));
        assertTrue(report.contains("20"));
    }

    @Test
    public void testBuildReportTextDoesNotContainPassword() throws Exception {
        GameManager gameManager = createGameManagerWithWanderer();
        Wanderer wanderer = getFirstWanderer(gameManager);

        WandererExporter exporter = new WandererExporter(gameManager);
        String report = exporter.buildReportText(wanderer);

        assertFalse(report.contains("himmel123"));
        assertFalse(report.toLowerCase().contains("password"));
    }

    @Test
    public void testPreviewWandererReportReturnsReportText() throws Exception {
        GameManager gameManager = createGameManagerWithWanderer();
        Wanderer wanderer = getFirstWanderer(gameManager);

        WandererExporter exporter = new WandererExporter(gameManager);
        String preview = exporter.previewWandererReport(wanderer);

        assertTrue(preview.contains("LAPORAN DATA PENGEMBARA"));
        assertTrue(preview.contains("Frieren"));
        assertTrue(preview.contains("frieren"));
        assertTrue(preview.contains("Mage"));
    }

    @Test
    public void testExportToTxtCreatesFileWithAllWandererReports() throws Exception {
        GameManager gameManager = createGameManagerWithWanderer();
        WandererExporter exporter = new WandererExporter(gameManager);

        File outputFile = tempFolder.newFile("wanderers_report.txt");

        exporter.exportToTxt(outputFile.getAbsolutePath(), AdminIOCsvMode.WANDERER);

        String content = new String(
            Files.readAllBytes(outputFile.toPath()),
            StandardCharsets.UTF_8
        );

        assertTrue(content.contains("Data Pengembara BurhanQuest"));
        assertTrue(content.contains("Total pengembara: 1"));
        assertTrue(content.contains("LAPORAN DATA PENGEMBARA"));
        assertTrue(content.contains("Frieren"));
        assertTrue(content.contains("frieren"));
        assertTrue(content.contains("Mage"));
        assertTrue(content.contains("100/100"));
        assertFalse(content.contains("himmel123"));
    }

    @Test
    public void testExportToTxtExportsMultipleWanderers() throws Exception {
        GameManager gameManager = createGameManagerWithWanderer();

        gameManager.addWanderer(
            "Himmel",
            "himmel",
            "hero123",
            150,
            70,
            15,
            "5"
        );

        WandererExporter exporter = new WandererExporter(gameManager);
        File outputFile = tempFolder.newFile("multiple_wanderers.txt");

        exporter.exportToTxt(outputFile.getAbsolutePath(), AdminIOCsvMode.WANDERER);

        String content = new String(
            Files.readAllBytes(outputFile.toPath()),
            StandardCharsets.UTF_8
        );

        assertTrue(content.contains("Total pengembara: 2"));
        assertTrue(content.contains("Frieren"));
        assertTrue(content.contains("Himmel"));
        assertTrue(content.contains("frieren"));
        assertTrue(content.contains("himmel"));
        assertFalse(content.contains("himmel123"));
        assertFalse(content.contains("hero123"));
    }

    @Test
    public void testExportToTxtThrowsInvalidFileTypeExceptionIfNotTxt() throws Exception {
        GameManager gameManager = createGameManagerWithWanderer();
        WandererExporter exporter = new WandererExporter(gameManager);

        File outputFile = tempFolder.newFile("wanderers_report.csv");

        try {
            exporter.exportToTxt(outputFile.getAbsolutePath(), AdminIOCsvMode.WANDERER);
            fail("Seharusnya melempar InvalidFileTypeException untuk file selain .txt");
        } catch (InvalidFileTypeException e) {
            assertEquals("kesalahan nama tipe file", e.getMessage());
        }
    }

    @Test
    public void testExportToTxtThrowsInvalidFormatExceptionIfModeQuest() throws Exception {
        GameManager gameManager = createGameManagerWithWanderer();
        WandererExporter exporter = new WandererExporter(gameManager);

        File outputFile = tempFolder.newFile("wanderers_report.txt");

        try {
            exporter.exportToTxt(outputFile.getAbsolutePath(), AdminIOCsvMode.QUEST);
            fail("Seharusnya melempar InvalidFormatException jika mode bukan WANDERER");
        } catch (InvalidFormatException e) {
            assertNotNull(e.getMessage());
        }
    }

    @Test
    public void testExportToCsvAlwaysThrowsInvalidFileTypeException() throws Exception {
        GameManager gameManager = createGameManagerWithWanderer();
        WandererExporter exporter = new WandererExporter(gameManager);

        File outputFile = tempFolder.newFile("wanderers_report.csv");

        try {
            exporter.exportToCsv(outputFile.getAbsolutePath(), AdminIOCsvMode.WANDERER);
            fail("WandererExporter tidak boleh export CSV");
        } catch (InvalidFileTypeException e) {
            assertEquals("kesalahan nama tipe file", e.getMessage());
        }
    }
}