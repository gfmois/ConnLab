package io.github.gfmois.connlab.db;


import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import io.github.gfmois.connlab.app.AppPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Database {
    private static final Logger logger = LogManager.getLogger(Database.class);
    private static String url;

    private Database() {
    }

    public static synchronized void init() {
        if (url != null) {
            logger.info("Database is already initialized.");
            return;
        }

        init(AppPaths.getDatabasePath());
    }

    /**
     * Initializes the database at the given file, creating or migrating the schema.
     * Replaces any previously initialized database.
     */
    public static synchronized void init(Path dbPath) {
        String newUrl = String.format("jdbc:sqlite:%s", dbPath);
        try (Connection connection = DriverManager.getConnection(newUrl)) {
            logger.info("Database connection established to {}", dbPath);
            createSchema(connection);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize database connection", e);
        }
        url = newUrl;
    }

    private static void createSchema(Connection connection) throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS app_settings (
                        id INTEGER PRIMARY KEY CHECK (id = 1),
                        configurations_path TEXT NOT NULL,
                        last_opened_configuration_id INTEGER,
                        max_log_lines INTEGER NOT NULL
                    );
                    """);
        }

        migrate(connection);
    }

    /**
     * Brings databases created by older versions up to date with the current schema.
     */
    private static void migrate(Connection connection) throws SQLException {
        if (!columnExists(connection, "app_settings", "last_opened_configuration_id")) {
            logger.info("Migrating database: adding column app_settings.last_opened_configuration_id");
            try (Statement st = connection.createStatement()) {
                st.executeUpdate("ALTER TABLE app_settings ADD COLUMN last_opened_configuration_id INTEGER");
            }
        }
    }

    private static boolean columnExists(Connection connection, String table, String column) throws SQLException {
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }

    public static Connection getConnection() throws SQLException {
        if (url == null) {
            init();
        }
        return DriverManager.getConnection(url);
    }
}
