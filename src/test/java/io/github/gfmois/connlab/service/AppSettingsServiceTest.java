package io.github.gfmois.connlab.service;

import io.github.gfmois.connlab.app.AppSettings;
import io.github.gfmois.connlab.db.Database;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AppSettingsServiceTest {
    @TempDir
    Path tempDir;

    private final AppSettingsService service = new AppSettingsService();

    @BeforeEach
    void setUp() {
        Database.init(tempDir.resolve("test.db"));
    }

    @Test
    void savesAnExistingDirectoryAsAnAbsolutePath() throws IOException {
        Path configs = Files.createDirectory(tempDir.resolve("configs"));

        AppSettings saved = service.save("  " + configs + "  ", 4, false);

        assertEquals(configs.toAbsolutePath().toString(), saved.getConfigurationsPath());
        assertEquals(configs.toAbsolutePath().toString(), service.getAppSettings().getConfigurationsPath());
        assertEquals(4, service.getAppSettings().getLastConfigurationUsedId());
    }

    @Test
    void createsTheDirectoryWhenAskedTo() {
        Path configs = tempDir.resolve("new/nested/configs");

        service.save(configs.toString(), null, true);

        assertTrue(Files.isDirectory(configs));
    }

    @Test
    void rejectsAMissingDirectoryWhenNotAskedToCreateIt() {
        Path configs = tempDir.resolve("missing");

        assertThrows(IllegalArgumentException.class, () -> service.save(configs.toString(), null, false));
        assertFalse(Files.exists(configs));
    }

    @Test
    void rejectsAFile() throws IOException {
        Path file = Files.createFile(tempDir.resolve("not-a-directory.json"));

        assertThrows(IllegalArgumentException.class, () -> service.save(file.toString(), null, true));
    }

    @Test
    void rejectsAnEmptyPath() {
        assertThrows(IllegalArgumentException.class, () -> service.save(null, null, true));
        assertThrows(IllegalArgumentException.class, () -> service.save("", null, true));
        assertThrows(IllegalArgumentException.class, () -> service.save("   ", null, true));
    }

    @Test
    void removeAppConfigurationClearsThePath() throws IOException {
        Path configs = Files.createDirectory(tempDir.resolve("configs"));
        service.save(configs.toString(), null, false);

        service.removeAppConfiguration();

        assertNull(service.getAppSettings().getConfigurationsPath());
    }
}
