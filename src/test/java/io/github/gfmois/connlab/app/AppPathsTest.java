package io.github.gfmois.connlab.app;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AppPathsTest {
    @TempDir
    Path home;
    private String originalHome;

    @BeforeEach
    void useTemporaryHome() {
        originalHome = System.getProperty("user.home");
        System.setProperty("user.home", home.toString());
    }

    @AfterEach
    void restoreHome() {
        System.setProperty("user.home", originalHome);
    }

    @Test
    void everythingLivesInTheAppFolderOfTheUserHome() {
        Path appDir = home.resolve(".labtcp");

        assertEquals(appDir, AppPaths.getAppDirectory());
        assertEquals(appDir.resolve("logs"), AppPaths.getLogsPath());
        assertEquals(appDir.resolve("db").resolve("labtcp.db"), AppPaths.getDatabasePath());
    }

    @Test
    void theFoldersAreCreated() {
        assertTrue(Files.isDirectory(AppPaths.getLogsPath()));
        assertTrue(Files.isDirectory(AppPaths.getDatabasePath().getParent()));
    }
}
