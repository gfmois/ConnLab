package io.github.gfmois.connlab.db;

import io.github.gfmois.connlab.app.AppSettings;
import io.github.gfmois.connlab.db.dao.AppSettingsDao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseTest {
    @TempDir
    Path tempDir;

    private Path dbPath;
    private final AppSettingsDao dao = new AppSettingsDao();

    @BeforeEach
    void setUp() {
        dbPath = tempDir.resolve("test.db");
    }

    private List<String> columns() throws SQLException {
        List<String> columns = new ArrayList<>();
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(app_settings)")) {
            while (rs.next()) columns.add(rs.getString("name"));
        }
        return columns;
    }

    private void execute(String sql) throws SQLException {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             Statement st = c.createStatement()) {
            st.executeUpdate(sql);
        }
    }

    private static AppSettings settings(String path, Integer lastConfigurationId, int maxLogLines) {
        return new AppSettings(lastConfigurationId, path, maxLogLines);
    }

    @Test
    void createsTheCurrentSchemaOnANewDatabase() throws SQLException {
        Database.init(dbPath);

        assertEquals(List.of("id", "configurations_path", "last_opened_configuration_id", "max_log_lines"), columns());
    }

    @Test
    void migratesADatabaseCreatedByAnOlderVersionKeepingItsData() throws SQLException {
        // Schema of the versions up to 1.2.6, which lacked last_opened_configuration_id
        execute("""
                CREATE TABLE app_settings (
                    id INTEGER PRIMARY KEY CHECK (id = 1),
                    configurations_path TEXT NOT NULL,
                    max_log_lines INTEGER NOT NULL
                )""");
        execute("INSERT INTO app_settings (id, configurations_path, max_log_lines) VALUES (1, '/old/configs', 500)");

        Database.init(dbPath);

        assertTrue(columns().contains("last_opened_configuration_id"));
        AppSettings migrated = dao.retrieveSettings();
        assertEquals("/old/configs", migrated.getConfigurationsPath());
        assertEquals(500, migrated.getMaxLogsLines());
        assertNull(migrated.getLastConfigurationUsedId());
    }

    @Test
    void initializingTwiceIsSafe() throws SQLException {
        Database.init(dbPath);
        dao.saveSettings(settings("/configs", 3, 100));

        Database.init(dbPath);

        assertEquals(4, columns().size());
        assertEquals("/configs", dao.retrieveSettings().getConfigurationsPath());
    }

    @Test
    void emptyDatabaseGivesDefaultSettings() {
        Database.init(dbPath);

        AppSettings settings = dao.retrieveSettings();

        assertNull(settings.getConfigurationsPath());
        assertNull(settings.getLastConfigurationUsedId());
        assertEquals(1000, settings.getMaxLogsLines());
    }

    @Test
    void savesAndRetrievesSettings() {
        Database.init(dbPath);

        dao.saveSettings(settings("/configs", 7, 250));

        AppSettings read = dao.retrieveSettings();
        assertEquals("/configs", read.getConfigurationsPath());
        assertEquals(7, read.getLastConfigurationUsedId());
        assertEquals(250, read.getMaxLogsLines());
    }

    @Test
    void nullLastConfigurationIsReadAsNullNotZero() {
        Database.init(dbPath);

        dao.saveSettings(settings("/configs", null, 250));

        assertNull(dao.retrieveSettings().getLastConfigurationUsedId());
    }

    @Test
    void savingAgainReplacesTheSingleSettingsRow() throws SQLException {
        Database.init(dbPath);

        dao.saveSettings(settings("/first", 1, 100));
        dao.saveSettings(settings("/second", 2, 200));

        assertEquals("/second", dao.retrieveSettings().getConfigurationsPath());
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM app_settings")) {
            assertEquals(1, rs.getInt(1));
        }
    }

    @Test
    void nonPositiveMaxLogLinesFallsBackToTheDefault() throws SQLException {
        Database.init(dbPath);
        execute("INSERT INTO app_settings (id, configurations_path, max_log_lines) VALUES (1, '/configs', 0)");

        assertEquals(1000, dao.retrieveSettings().getMaxLogsLines());
    }

    @Test
    void removeDeletesTheSettings() {
        Database.init(dbPath);
        dao.saveSettings(settings("/configs", 1, 100));

        dao.removeAppSettings();

        assertNull(dao.retrieveSettings().getConfigurationsPath());
    }
}
