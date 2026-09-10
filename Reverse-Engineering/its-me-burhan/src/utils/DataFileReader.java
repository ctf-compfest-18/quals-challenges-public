package utils;

import exception.DataFileException;
import exception.InvalidFileTypeException;
import exception.InvalidFormatException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class DataFileReader {
    private static final File IMPORT_ROOT = canonicalRoot();

    private final Scanner scanner;
    private final String path;
    private int expectedColumnCount;
    private int lineNumber;

    public DataFileReader(String path) throws DataFileException {
        this.path = path;
        this.expectedColumnCount = -1;
        this.lineNumber = 0;

        if (path == null || !path.toLowerCase().endsWith(".csv")) {
            throw new InvalidFileTypeException(path);
        }

        File file = resolveWithinRoot(path);

        try {
            this.scanner = new Scanner(file);
        } catch (FileNotFoundException e) {
            throw new DataFileException("kesalahan nama file", e);
        }
    }

    private static File canonicalRoot() {
        File dir = new File(System.getProperty("user.dir"));
        try {
            return dir.getCanonicalFile();
        } catch (IOException e) {
            return dir.getAbsoluteFile();
        }
    }

    private File resolveWithinRoot(String requested) throws DataFileException {
        try {
            File candidate = new File(requested);
            if (!candidate.isAbsolute()) {
                candidate = new File(IMPORT_ROOT, requested);
            }
            File canonical = candidate.getCanonicalFile();
            if (!canonical.toPath().startsWith(IMPORT_ROOT.toPath())) {
                throw new InvalidFileTypeException(requested);
            }
            return canonical;
        } catch (IOException e) {
            throw new DataFileException("kesalahan nama file", e);
        }
    }

    public List<String> readNext() throws DataFileException {
        if (!scanner.hasNextLine()) {
            return null;
        }

        String line = scanner.nextLine();
        lineNumber++;
        List<String> values = parseCsvLine(line);

        if (expectedColumnCount == -1) {
            expectedColumnCount = values.size();
        } else if (values.size() != expectedColumnCount) {
            throw new InvalidFormatException(path);
        }

        return values;
    }

    public List<List<String>> readAll() throws DataFileException {
        List<List<String>> rows = new ArrayList<>();
        List<String> row;
        while ((row = readNext()) != null) {
            rows.add(row);
        }
        return rows;
    }

    public void close() {
        scanner.close();
    }

    private List<String> parseCsvLine(String line) throws DataFileException {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuote = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (c == '"') {
                if (inQuote && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuote = !inQuote;
                }
            } else if (c == ',' && !inQuote) {
                result.add(normalizeCell(current.toString()));
                current.setLength(0);
            } else {
                current.append(c);
            }
        }

        if (inQuote) {
            throw new InvalidFormatException(path);
        }

        result.add(normalizeCell(current.toString()));
        return result;
    }

    private String normalizeCell(String value) {
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}