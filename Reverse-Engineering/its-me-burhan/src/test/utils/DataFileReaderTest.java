package test.utils;

import exception.*;
import utils.*;

import static org.junit.Assert.*;

import java.io.File;
import java.io.PrintWriter;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class DataFileReaderTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private File createFile(String fileName, String content) throws Exception {
        File file = new File(temporaryFolder.getRoot(), fileName);
        PrintWriter writer = new PrintWriter(file);
        writer.print(content);
        writer.close();
        return file;
    }

    @Test
    public void testReadAllParsesCsvRowsAndQuotedValues() throws Exception {
        File file = createFile(
                "quests.csv",
                "name,description,type\n" +
                "\"Sarang, Naga\",\"Dia bilang \"\"rawr\"\"\",bounty\n" +
                "Latihan Harian,,daily\n"
        );

        DataFileReader reader = new DataFileReader(file.getAbsolutePath());
        List<List<String>> rows = reader.readAll();
        reader.close();

        assertEquals(3, rows.size());
        assertEquals("name", rows.get(0).get(0));
        assertEquals("Sarang, Naga", rows.get(1).get(0));
        assertEquals("Dia bilang \"rawr\"", rows.get(1).get(1));
        assertEquals("bounty", rows.get(1).get(2));
        assertEquals("Latihan Harian", rows.get(2).get(0));
        assertNull(rows.get(2).get(1));
        assertEquals("daily", rows.get(2).get(2));
    }

    @Test
    public void testReadNextReturnsNullAfterLastLine() throws Exception {
        File file = createFile("single.csv", "a,b\n1,2\n");

        DataFileReader reader = new DataFileReader(file.getAbsolutePath());
        assertNotNull(reader.readNext());
        assertNotNull(reader.readNext());
        assertNull(reader.readNext());
        reader.close();
    }

    @Test
    public void testConstructorThrowsForInvalidFileType() throws Exception {
        try {
            new DataFileReader("salah.txt");
            fail("Seharusnya melempar InvalidFileTypeException untuk file non-csv.");
        } catch (InvalidFileTypeException e) {
            assertEquals("kesalahan nama tipe file", e.getMessage());
        }
    }

    @Test
    public void testConstructorThrowsForMissingFile() throws Exception {
        File file = new File(temporaryFolder.getRoot(), "tidak_ada.csv");

        try {
            new DataFileReader(file.getAbsolutePath());
            fail("Seharusnya melempar DataFileException untuk file yang tidak ditemukan.");
        } catch (DataFileException e) {
            assertEquals("kesalahan nama file", e.getMessage());
            assertNotNull(e.getCause());
        }
    }

    @Test
    public void testReadAllThrowsForDifferentColumnCount() throws Exception {
        File file = createFile("invalid.csv", "a,b\n1,2,3\n");

        DataFileReader reader = new DataFileReader(file.getAbsolutePath());
        try {
            reader.readAll();
            fail("Seharusnya melempar DataFileException karena jumlah kolom tidak konsisten.");
        } catch (DataFileException e) {
            assertTrue(e.getMessage().toLowerCase().contains("format"));
        } finally {
            reader.close();
        }
    }

    @Test
    public void testReadAllThrowsForUnclosedQuote() throws Exception {
        File file = createFile("invalid_quote.csv", "a,b\n\"belum selesai,b\n");

        DataFileReader reader = new DataFileReader(file.getAbsolutePath());
        try {
            reader.readAll();
            fail("Seharusnya melempar DataFileException karena quote belum ditutup.");
        } catch (DataFileException e) {
            assertTrue(e.getMessage().toLowerCase().contains("format"));
        } finally {
            reader.close();
        }
    }
}