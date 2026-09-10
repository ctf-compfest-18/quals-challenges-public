package quests.enums;

import java.util.Arrays;
import java.util.Optional;

public enum Difficulty {
    MUDAH("mudah", 1),
    MENENGAH("menengah", 6),
    SULIT("sulit", 16);

    private final String displayName;
    private final int minWandererLevel;

    Difficulty(String displayName, int minWandererLevel) {
        this.displayName = displayName;
        this.minWandererLevel = minWandererLevel;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getMinWandererLevel() {
        return minWandererLevel;
    }

    public static Optional<Difficulty> fromString(String raw) {
        if (raw == null || raw.isEmpty()) {
            return Optional.empty();
        }

        String normalized = raw.trim();
        return Arrays.stream(Difficulty.values())
                .filter(difficulty -> difficulty.displayName.equalsIgnoreCase(normalized))
                .findFirst();
    }
}
