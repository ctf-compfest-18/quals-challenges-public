package utils;

import exception.DataFileException;
import exception.InvalidFileTypeException;
import utils.enums.DataFileWriterMode;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class DataFileWriter {
    private final PrintWriter printWriter;
    private final DataFileWriterMode mode;

    public DataFileWriter(String path, DataFileWriterMode mode) throws DataFileException {
        this.mode = mode;

        if (path == null || !isValidExtension(path, mode)) {
            throw new InvalidFileTypeException(path);
        }

        try {
            this.printWriter = new PrintWriter(path);
        } catch (FileNotFoundException e) {
            throw new DataFileException("kesalahan nama file", e);
        }
    }

    public static DataFileWriter forCsv(String path) throws DataFileException {
        return new DataFileWriter(path, DataFileWriterMode.CSV);
    }

    public static DataFileWriter forTxt(String path) throws DataFileException {
        return new DataFileWriter(path, DataFileWriterMode.TXT);
    }

    public void writeRow(List<String> row) {
        if (mode == DataFileWriterMode.CSV) {
            writeCsvRow(row);
        } else {
            writeTxtRow(row);
        }
    }

    public void writeHeader(List<String> row) {
        writeRow(row);
    }

    public void writeSeparator() {
        if (mode == DataFileWriterMode.TXT) {
            printWriter.println(repeat("-", 40));
        }
    }

    public void close() {
        printWriter.close();
    }

    public void writeLine(String line) {
        printWriter.println(line == null ? "" : line);
    }

    public void writeCsvRow(List<String> values) {
        String row = values == null
                ? ""
                : values.stream()
                    .map(this::escapeCsv)
                    .collect(Collectors.joining(","));
        printWriter.println(row);
    }

    public void writeTable(List<String> headers, List<List<String>> rows) {
        if (headers == null || headers.isEmpty()) {
            return;
        }

        int[] widths = IntStream.range(0, headers.size())
                .map(i -> Math.max(
                    safe(headers.get(i)).length(),
                    maxRowWidthAt(rows, i)
                ))
                .toArray();

        writeTableSeparator(widths);
        writeTableRow(headers, widths);
        writeTableSeparator(widths);
        if (rows != null) {
            rows.stream()
                .filter(row -> row != null)
                .forEach(row -> writeTableRow(row, widths));
        }
        writeTableSeparator(widths);
    }

    private int maxRowWidthAt(List<List<String>> rows, int index) {
        if (rows == null) {
            return 0;
        }
        return rows.stream()
                .filter(row -> row != null && index < row.size())
                .map(row -> safe(row.get(index)))
                .mapToInt(String::length)
                .max()
                .orElse(0);
    }

    private void writeTxtRow(List<String> row) {
        String body = row == null
                ? ""
                : row.stream()
                    .map(value -> " " + safe(value) + " |")
                    .collect(Collectors.joining());
        printWriter.println("|" + body);
    }

    private boolean isValidExtension(String path, DataFileWriterMode mode) {
        String lowerPath = path.toLowerCase();
        if (mode == DataFileWriterMode.CSV) {
            return lowerPath.endsWith(".csv");
        }
        return lowerPath.endsWith(".txt");
    }

    private String escapeCsv(String value) {
        String safeValue = safe(value);
        if (safeValue.contains(",") || safeValue.contains("\"") || safeValue.contains("\n")) {
            return "\"" + safeValue.replace("\"", "\"\"") + "\"";
        }
        return safeValue;
    }

    private void writeTableSeparator(int[] widths) {
        String separator = Arrays.stream(widths)
                .mapToObj(width -> repeat("-", width + 2))
                .collect(Collectors.joining("+", "+", "+"));
        printWriter.println(separator);
    }

    private void writeTableRow(List<String> values, int[] widths) {
        String row = IntStream.range(0, widths.length)
                .mapToObj(i -> {
                    String value = values != null && i < values.size() ? safe(values.get(i)) : "";
                    return " " + padRight(value, widths[i]) + " ";
                })
                .collect(Collectors.joining("|", "|", "|"));
        printWriter.println(row);
    }

    private String padRight(String value, int width) {
        return value + repeat(" ", width - value.length());
    }

    private String repeat(String value, int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> value)
                .collect(Collectors.joining());
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
