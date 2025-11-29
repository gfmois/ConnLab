package io.github.gfmois.labtcp.app;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class AppPaths {
    private static final String APP_DIR_NAME = ".labtcp";

    private AppPaths() {
    }

    public static Path getAppDirectory() {
        String userHome = System.getProperty("user.home");
        Path dir = Path.of(userHome, APP_DIR_NAME);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create application directory: " + dir, e);
        }

        return dir;
    }

    public static Path getDatabasePath() {
        Path dbDir = getAppDirectory().resolve("db");
        try {
            Files.createDirectories(dbDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create database directory: " + dbDir, e);
        }

        return dbDir.resolve("labtcp.db");
    }

    public static Path getLogsPath() {
        Path logsDir = getAppDirectory().resolve("logs");
        try {
            Files.createDirectories(logsDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create logs directory: " + logsDir, e);
        }

        return logsDir;
    }
}
