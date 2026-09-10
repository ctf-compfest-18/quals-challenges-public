package utils;

import entities.User;
import entities.Wanderer;

import exception.DataFileException;
import exception.InvalidFileTypeException;
import exception.InvalidFormatException;

import services.GameManager;

import quests.Quest;

import utils.DataFileWriter;
import utils.enums.AdminIOCsvMode;
import utils.interfaces.DataExportable;

import java.lang.reflect.Method;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class WandererExporter implements DataExportable {
    private final GameManager gameManager;
    private static final DecimalFormat DF = new DecimalFormat("#.##");
    private static final int REPORT_WIDTH = 40;

    public WandererExporter(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @Override
    public void exportToCsv(String path, AdminIOCsvMode mode) throws DataFileException {
        throw new InvalidFileTypeException(path);
    }

    @Override
    public void exportToTxt(String path, AdminIOCsvMode mode) throws DataFileException {
        if (mode != AdminIOCsvMode.WANDERER) {
            throw new InvalidFormatException(path);
        }

        DataFileWriter writer = DataFileWriter.forTxt(path);
        try {
            writer.writeLine("=== Data Pengembara BurhanQuest ===");
            writer.writeLine("Total pengembara: " + gameManager.getWanderersCount());
            writer.writeLine("");

            gameManager.getWanderers().stream()
                .map(user -> (Wanderer) user)
                .map(this::previewWandererReport)
                .forEach(report -> {
                    writer.writeLine(report);
                    writer.writeLine("");
                });
        } finally {
            writer.close();
        }
    }

    public void exportToTxt(String path, Wanderer wanderer) throws DataFileException {
        DataFileWriter writer = DataFileWriter.forTxt(path);
        try {
            writer.writeLine(previewWandererReport(wanderer));
        } finally {
            writer.close();
        }
    }

    public String previewWandererReport(Wanderer wanderer) {
        StringBuilder sb = new StringBuilder();

        sb.append(repeat("=", REPORT_WIDTH)).append("\n");
        sb.append(center("LAPORAN DATA PENGEMBARA", REPORT_WIDTH)).append("\n");
        sb.append(repeat("=", REPORT_WIDTH)).append("\n");

        appendField(sb, "ID Pengembara", wanderer.getId());
        appendField(sb, "Nama", wanderer.getName());
        appendField(sb, "Username", wanderer.getUsername());
        appendField(sb, "Job", getJobClass(wanderer));
        appendField(sb, "Level", String.valueOf(wanderer.getLevel()));
        appendField(sb, "Exp", wanderer.getExp() + " poin exp");
        appendField(sb, "Koin", wanderer.getCoins() + " koin");
        appendField(sb, "HP", formatNumber(wanderer.getCurrentHp()) + "/" + formatNumber(wanderer.getMaxHp()));
        appendField(sb, "Attack", formatNumber(wanderer.getAttackPower()));
        appendField(sb, "Defense", formatNumber(wanderer.getDefense()));

        sb.append(repeat("-", REPORT_WIDTH)).append("\n");
        sb.append(String.format("%-16s: %n", "Riwayat Quest"));

        boolean hasHistory = false;
        hasHistory = appendQuestHistory(sb, wanderer, "getCompletedQuestHistory", "[SELESAI]") || hasHistory;
        hasHistory = appendQuestHistory(sb, wanderer, "getFailedQuestHistory", "[KALAH]") || hasHistory;
        hasHistory = appendQuestHistory(sb, wanderer, "getLostQuestHistory", "[KALAH]") || hasHistory;

        if (!hasHistory) {
            sb.append("  Belum ada riwayat quest.\n");
        }

        sb.append(repeat("=", REPORT_WIDTH));

        return sb.toString();
    }

    public String buildReportText(Wanderer wanderer) {
        return previewWandererReport(wanderer);
    }

    private void appendField(StringBuilder sb, String label, String value) {
        sb.append(String.format("%-16s: %s%n", label, value));
    }

    private boolean appendQuestHistory(StringBuilder sb, Wanderer wanderer, String methodName, String status) {
        try {
            Method method = wanderer.getClass().getMethod(methodName);
            Object result = method.invoke(wanderer);

            if (!(result instanceof List<?>)) {
                return false;
            }

            List<?> history = (List<?>) result;

            if (history.isEmpty()) {
                return false;
            }

            history.stream()
                .forEach(item -> appendSingleQuestHistory(sb, item, status));

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void appendSingleQuestHistory(StringBuilder sb, Object item, String status) {
        String questId = getValueByMethod(item, "getId");
        String questName = getValueByMethod(item, "getName");
        String day = getQuestDay(item);

        if (questId.isEmpty() && item instanceof Quest) {
            Quest quest = (Quest) item;
            questId = quest.getId();
            questName = quest.getName();
        }

        if (questId.isEmpty()) {
            questId = "-";
        }

        if (questName.isEmpty()) {
            questName = "-";
        }

        sb.append("  ")
          .append(String.format("%-9s", status))
          .append(" ")
          .append(questId)
          .append(" - ")
          .append(questName);

        if (!day.isEmpty()) {
            sb.append(" (Hari ke-").append(day).append(")");
        }

        sb.append("\n");
    }

    private String getQuestDay(Object item) {
        String[] possibleMethods = {
            "getDay",
            "getQuestDay",
            "getCompletedDay",
            "getFinishedDay",
            "getBattleDay"
        };

        return Arrays.stream(possibleMethods)
            .map(methodName -> getValueByMethod(item, methodName))
            .filter(value -> !value.isEmpty())
            .findFirst()
            .orElse("");
    }

    private String getValueByMethod(Object object, String methodName) {
        try {
            Method method = object.getClass().getMethod(methodName);
            Object value = method.invoke(object);

            if (value == null) {
                return "";
            }

            return String.valueOf(value);
        } catch (Exception e) {
            return "";
        }
    }

    private String getJobClass(Wanderer wanderer) {
        String simpleName = wanderer.getClass().getSimpleName();

        if (simpleName.equals("Wanderer")) {
            return "Novice";
        }

        return simpleName;
    }

    private String formatNumber(double value) {
        return DF.format(value);
    }

    private String center(String text, int width) {
        if (text.length() >= width) {
            return text;
        }

        int leftPadding = (width - text.length()) / 2;
        int rightPadding = width - text.length() - leftPadding;

        return repeat(" ", leftPadding) + text + repeat(" ", rightPadding);
    }

    private String repeat(String text, int count) {
        return IntStream.range(0, count)
            .mapToObj(i -> text)
            .collect(Collectors.joining());
    }
}
