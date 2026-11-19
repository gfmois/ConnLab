package io.github.gfmois.connlab.db.dao;

import io.github.gfmois.connlab.app.AppSettings;
import io.github.gfmois.connlab.db.Database;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class AppSettingsDao {
    private static final Logger logger = LogManager.getLogger(AppSettingsDao.class);

    public AppSettings retrieveSettings() {
        String sql = "SELECT configurations_path, last_opened_configuration_id, max_log_lines FROM app_settings LIMIT 1";

        try (Connection conn = Database.getConnection(); PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            AppSettings appSettings = new AppSettings();
            if (rs.next()) {
                appSettings.setConfigurationsPath(rs.getString("configurations_path"));
                int lastConfigurationId = rs.getInt("last_opened_configuration_id");
                appSettings.setLastConfigurationUsedId(rs.wasNull() ? null : lastConfigurationId);
                int maxLogLines = rs.getInt("max_log_lines");
                if (maxLogLines > 0) {
                    appSettings.setMaxLogsLines(maxLogLines);
                }
            }

            return appSettings;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to retrieve application settings", e);
        }
    }

    public void saveSettings(AppSettings settings) {
        String sql = """
                INSERT INTO app_settings (id, configurations_path, last_opened_configuration_id, max_log_lines)
                VALUES (1, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    configurations_path = excluded.configurations_path,
                    last_opened_configuration_id = excluded.last_opened_configuration_id,
                    max_log_lines = excluded.max_log_lines
                """;

        try (Connection conn = Database.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, settings.getConfigurationsPath());
            if (settings.getLastConfigurationUsedId() == null) {
                ps.setNull(2, java.sql.Types.INTEGER);
            } else {
                ps.setInt(2, settings.getLastConfigurationUsedId());
            }
            ps.setInt(3, settings.getMaxLogsLines());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save application settings", e);
        }
    }

    public void removeAppSettings() {
        String sql = "DELETE FROM app_settings";

        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int rowsAffected = ps.executeUpdate();
            logger.info("Removed {} rows", rowsAffected);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to remove app settings", e);
        }
    }
}
