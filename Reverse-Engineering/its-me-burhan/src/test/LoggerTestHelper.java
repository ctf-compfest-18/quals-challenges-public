package test;

import utils.*;

import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;

public class LoggerTestHelper {
    public static File isolateLogger(TemporaryFolder tempFolder) throws IOException {
        File logFile = tempFolder.newFile("guild_history_test.log");
        BurhanLogger.getInstance().setFilePath(logFile.getAbsolutePath());
        return logFile;
    }

    public static void resetLogger() {
        BurhanLogger.getInstance().setFilePath("guild_history.log");
    }
}