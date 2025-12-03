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
                    CREATE TABLE IF NOT EXISTS app_settings (
                        id INTEGER PRIMARY KEY CHECK (id = 1),
                        last_opened_configuration_id INTEGER,
                        configurations_path TEXT NOT NULL,
                        max_log_lines INTEGER NOT NULL
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
