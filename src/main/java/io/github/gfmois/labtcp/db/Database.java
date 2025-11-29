package io.github.gfmois.labtcp.db;


import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import io.github.gfmois.labtcp.app.AppPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Database {
    private static final Logger logger = LogManager.getLogger(Database.class);
    private static String url;

    private Database() {
    }

    public static void init() {
        if (url != null) {
            logger.info("Database is already initialized.");
            return;
        }

        try {
            Path dbPath = AppPaths.getDatabasePath();
            url = String.format("jdbc:sqlite:%s", dbPath);

            // Establish connection
            try (Connection connection = DriverManager.getConnection(url)) {
                logger.info("Database connection established to {}", dbPath);
                createSchema(connection);
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize database connection", e);
        }
    }

    private static void createSchema(Connection connection) throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.executeUpdate(""" 
                       CREATE TABLE IF NOT EXISTS configurations (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL UNIQUE,
                        description TEXT,
                        config_path TEXT NOT NULL,
                        broadcast INTEGER NOT NULL DEFAULT 0,
                        has_to_respond INTEGER NOT NULL DEFAULT 0
                    );
                    
                    CREATE TABLE IF NOT EXISTS messages (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        configuration_id INTEGER NOT NULL,
                        received_regex TEXT NOT NULL,
                        FOREIGN KEY (configuration_id) REFERENCES configurations(id) ON DELETE CASCADE
                    );
                    
                    CREATE TABLE IF NOT EXISTS message_parts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        message_id INTEGER NOT NULL,
                        type TEXT NOT NULL,
                        text TEXT,
                        part_index INTEGER,
                        FOREIGN KEY (message_id) REFERENCES messages(id) ON DELETE CASCADE
                    );
                    
                    CREATE TABLE IF NOT EXISTS app_settings (
                        id INTEGER PRIMARY KEY CHECK (id = 1),
                        last_opened_configuration_id INTEGER,
                        configurations_path TEXT NOT NULL,
                        FOREIGN KEY (last_opened_configuration_id) REFERENCES configurations(id)
                    );
                    """);
        }
    }

    public static Connection getConnection() throws SQLException {
        if (url == null) {
            init();
        }
        return DriverManager.getConnection(url);
    }
}
