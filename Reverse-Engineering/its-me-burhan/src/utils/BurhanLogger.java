package utils;

import utils.enums.LogCategory;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;

public class BurhanLogger {
    private static BurhanLogger instance;
    private String filePath = "guild_history.log";

    private BurhanLogger() {
    }

    public static BurhanLogger getInstance() {
        if (instance == null) {
            instance = new BurhanLogger();
        }
        return instance;
    }

    public void setFilePath(String path) {
        if (path == null || path.trim().isEmpty()) {
            this.filePath = "guild_history.log";
        } else {
            this.filePath = path;
        }
    }

    public String getFilePath() {
        return this.filePath;
    }

    public synchronized void log(LogCategory category, String actor, String action) {
        String timestamp = Instant.now().toString();

        String message = String.format(
            "[%s] [%s] [%s] - %s",
            timestamp,
            category,
            actor,
            action
        );

        writeToFile(message);
    }

    public synchronized void log(LogCategory category, String actor, String action, Exception e) {
        if (e == null) {
            log(category, actor, action);
            return;
        }

        String exceptionMessage = e.getMessage();

        if (exceptionMessage == null || exceptionMessage.trim().isEmpty()) {
            exceptionMessage = e.getClass().getSimpleName();
        } else {
            exceptionMessage = e.getClass().getSimpleName() + ": " + exceptionMessage;
        }

        log(category, actor, action + " Error: " + exceptionMessage);
    }

    private void writeToFile(String message) {
        File file = new File(filePath);

        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, true))) {
            writer.write(message);
            writer.newLine();
        } catch (IOException e) {
            throw new IllegalStateException("Gagal menulis log ke " + filePath, e);
        }
    }
}