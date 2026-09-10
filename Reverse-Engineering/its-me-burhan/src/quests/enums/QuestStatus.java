package quests.enums;

public enum QuestStatus {
    TERSEDIA("tersedia"),
    SELESAI("selesai");

    private final String displayName;

    QuestStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static QuestStatus fromString(String raw) {
        if (raw == null) return null;
        for (QuestStatus status : QuestStatus.values()) {
            if (status.displayName.equalsIgnoreCase(raw.trim())) {
                return status;
            }
        }
        return null;
    }
}