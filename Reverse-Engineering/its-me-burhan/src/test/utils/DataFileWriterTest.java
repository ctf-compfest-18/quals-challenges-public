package test.utils;

import exception.*;
import utils.*;

import static org.junit.Assert.*;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class DataFileWriterTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void testForCsvWritesCsvRow() throws Exception {
        File file = folder.newFile("report.csv");

        DataFileWriter writer = DataFileWriter.forCsv(file.getAbsolutePath());
        writer.writeRow(Arrays.asList("id", "name", "coin"));
        writer.writeRow(Arrays.asList("P1", "Frieren", "5000"));
        writer.close();

        List<String> lines = Files.readAllLines(file.toPath());

        assertEquals(2, lines.size());
        assertEquals("id,name,coin", lines.get(0));
        assertEquals("P1,Frieren,5000", lines.get(1));
    }

    @Test
    public void testForCsvEscapesCommaAndQuote() throws Exception {
        File file = folder.newFile("escaped.csv");

        DataFileWriter writer = DataFileWriter.forCsv(file.getAbsolutePath());
        writer.writeRow(Arrays.asList("Q1", "Sarang, Naga", "Katanya \"sulit\""));
        writer.close();

        List<String> lines = Files.readAllLines(file.toPath());

        assertEquals(1, lines.size());
        assertEquals("Q1,\"Sarang, Naga\",\"Katanya \"\"sulit\"\"\"", lines.get(0));
    }

    @Test
    public void testForCsvRejectsTxtFile() {
        try {
            File file = new File(folder.getRoot(), "wrong.txt");
            DataFileWriter.forCsv(file.getAbsolutePath());
            fail("Seharusnya throw DataFileException untuk file csv dengan ekstensi salah.");
        } catch (DataFileException e) {
            assertEquals("kesalahan nama tipe file", e.getMessage());
        }
    }

    @Test
    public void testForTxtWritesHeaderSeparatorAndRow() throws Exception {
        File file = folder.newFile("report.txt");

        DataFileWriter writer = DataFileWriter.forTxt(file.getAbsolutePath());
        writer.writeHeader(Arrays.asList("ID", "Nama"));
        writer.writeSeparator();
        writer.writeRow(Arrays.asList("P1", "Frieren"));
        writer.close();

        List<String> lines = Files.readAllLines(file.toPath());

        assertEquals(3, lines.size());
        assertEquals("| ID | Nama |", lines.get(0));
        assertEquals("----------------------------------------", lines.get(1));
        assertEquals("| P1 | Frieren |", lines.get(2));
    }

    @Test
    public void testForTxtRejectsCsvFile() {
        try {
            File file = new File(folder.getRoot(), "wrong.csv");
            DataFileWriter.forTxt(file.getAbsolutePath());
            fail("Seharusnya throw DataFileException untuk file txt dengan ekstensi salah.");
        } catch (DataFileException e) {
            assertEquals("kesalahan nama tipe file", e.getMessage());
        }
    }

    @Test
    public void testWriteSeparatorDoesNothingForCsv() throws Exception {
        File file = folder.newFile("separator.csv");

        DataFileWriter writer = DataFileWriter.forCsv(file.getAbsolutePath());
        writer.writeRow(Arrays.asList("id", "name"));
        writer.writeSeparator();
        writer.writeRow(Arrays.asList("P1", "Frieren"));
        writer.close();

        List<String> lines = Files.readAllLines(file.toPath());

        assertEquals(2, lines.size());
        assertEquals("id,name", lines.get(0));
        assertEquals("P1,Frieren", lines.get(1));
    }

    @Test
    public void testWriteLineWritesPlainTextLine() throws Exception {
        File file = folder.newFile("line.txt");

        DataFileWriter writer = DataFileWriter.forTxt(file.getAbsolutePath());
        writer.writeLine("LAPORAN DATA PENGEMBARA");
        writer.writeLine(null);
        writer.writeLine("Selesai");
        writer.close();

        List<String> lines = Files.readAllLines(file.toPath());

        assertEquals(3, lines.size());
        assertEquals("LAPORAN DATA PENGEMBARA", lines.get(0));
        assertEquals("", lines.get(1));
        assertEquals("Selesai", lines.get(2));
    }

    @Test
    public void testWriteTableWritesReadableTable() throws Exception {
        File file = folder.newFile("table.txt");

        DataFileWriter writer = DataFileWriter.forTxt(file.getAbsolutePath());
        writer.writeTable(
            Arrays.asList("ID", "Nama"),
            Arrays.asList(
                Arrays.asList("P1", "Frieren"),
                Arrays.asList("P2", "Himmel")
            )
        );
        writer.close();

        List<String> lines = Files.readAllLines(file.toPath());

        assertEquals("+----+---------+", lines.get(0));
        assertEquals("| ID | Nama    |", lines.get(1));
        assertEquals("+----+---------+", lines.get(2));
        assertEquals("| P1 | Frieren |", lines.get(3));
        assertEquals("| P2 | Himmel  |", lines.get(4));
        assertEquals("+----+---------+", lines.get(5));
    }

    @Test
    public void testWriteRowHandlesNullValueAsEmptyString() throws Exception {
        File file = folder.newFile("null.csv");

        DataFileWriter writer = DataFileWriter.forCsv(file.getAbsolutePath());
        writer.writeRow(Arrays.asList("P1", null, "Frieren"));
        writer.close();

        List<String> lines = Files.readAllLines(file.toPath());

        assertEquals(1, lines.size());
        assertEquals("P1,,Frieren", lines.get(0));
    }

    @Test
    public void testCloseCanBeCalledMoreThanOnce() throws Exception {
        File file = folder.newFile("close.txt");

        DataFileWriter writer = DataFileWriter.forTxt(file.getAbsolutePath());
        writer.writeLine("test");
        writer.close();
        writer.close();

        List<String> lines = Files.readAllLines(file.toPath());

        assertEquals(1, lines.size());
        assertEquals("test", lines.get(0));
    }
}