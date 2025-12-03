package io.github.gfmois.labtcp.db.dao;

import io.github.gfmois.labtcp.app.AppSettings;
import io.github.gfmois.labtcp.db.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class AppSettingsDao {
    public AppSettings retrieveSettings() {
        String sql = "SELECT configurations_path, last_opened_configuration_id, max_log_lines FROM app_settings LIMIT 1";

        try (Connection conn = Database.getConnection(); PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            AppSettings appSettings = new AppSettings();
            if (rs.next()) {
                appSettings.setConfigurationsPath(rs.getString("configurations_path"));
                appSettings.setLastConfigurationUsedId(rs.getInt("last_opened_configuration_id"));
                appSettings.setMaxLogsLines(rs.getInt("max_log_lines"));
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
}
