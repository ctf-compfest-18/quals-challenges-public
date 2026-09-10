package utils;

import entities.Wanderer;

import quests.Quest;
import quests.enums.Difficulty;
import quests.enums.QuestStatus;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public final class CsvFieldCodec {

    private static final DecimalFormat DF = new DecimalFormat("#.##");

    private CsvFieldCodec() {
    }

    public static String formatNumber(double value) {
        return DF.format(value);
    }

    public static List<String> list(String... values) {
        return Arrays.stream(values)
            .collect(Collectors.toCollection(ArrayList::new));
    }

    public static String normalizeKey(String key) {
        return key == null ? "" : key.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    public static String defaultIfBlank(String value, String defaultValue) {
        return value == null || value.isEmpty() ? defaultValue : value;
    }

    public static String questTypeToCode(String type) {
        if (type.equals("daily")) {
            return "1";
        }
        if (type.equals("regular")) {
            return "2";
        }
        return "3";
    }

    public static String jobToCode(String job) {
        String normalized = job == null ? "" : job.toLowerCase();
        return switch (normalized) {
            case "1", "novice", "wanderer", "pengembara" -> "1";
            case "2", "tank" -> "2";
            case "3", "mage" -> "3";
            case "4", "assassin" -> "4";
            case "5", "fighter" -> "5";
            case "6", "support" -> "6";
            default -> null;
        };
    }

    public static String questTypeToCsv(Quest quest) {
        return quest.getQuestType().toLowerCase();
    }

    public static String difficultyToCsv(Difficulty difficulty) {
        return difficulty.getDisplayName().toLowerCase();
    }

    public static String statusToCsv(QuestStatus status) {
        return status.getDisplayName().toLowerCase();
    }

    public static String jobToCsv(Wanderer wanderer) {
        String simpleName = wanderer.getClass().getSimpleName();
        if (simpleName.equals("Wanderer")) {
            return "novice";
        }
        return simpleName.toLowerCase();
    }
}
