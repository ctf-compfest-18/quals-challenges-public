package utils;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ConsoleTablePrinter {

    public void printPreviewTable(String title, List<String> headers, List<List<String>> rows) {
        System.out.println("\n" + title);

        List<Integer> widths = IntStream.range(0, headers.size())
            .mapToObj(i -> Math.max(safe(headers.get(i)).length(), maxRowWidth(rows, i)))
            .collect(Collectors.toList());

        String separator = buildSeparator(widths);
        System.out.println(separator);
        printRow(headers, widths);
        System.out.println(separator);
        rows.forEach(row -> printRow(row, widths));
        System.out.println(separator + "\n");
    }

    private int maxRowWidth(List<List<String>> rows, int index) {
        return rows.stream()
            .map(row -> index < row.size() ? safe(row.get(index)) : "")
            .mapToInt(String::length)
            .max()
            .orElse(0);
    }

    private String buildSeparator(List<Integer> widths) {
        return widths.stream()
            .map(width -> "-".repeat(width + 2))
            .collect(Collectors.joining("+", "+", "+"));
    }

    private void printRow(List<String> values, List<Integer> widths) {
        String row = IntStream.range(0, widths.size())
            .mapToObj(i -> {
                String value = i < values.size() ? safe(values.get(i)) : "";
                return " " + value + " ".repeat(widths.get(i) - value.length() + 1);
            })
            .collect(Collectors.joining("|", "|", "|"));
        System.out.println(row);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
