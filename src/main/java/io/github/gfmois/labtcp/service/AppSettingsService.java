package io.github.gfmois.labtcp.service;

import io.github.gfmois.labtcp.app.AppSettings;
import io.github.gfmois.labtcp.db.dao.AppSettingsDao;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class AppSettingsService {
    private final AppSettingsDao appSettingsDao;

    public AppSettingsService() {
        this.appSettingsDao = new AppSettingsDao();
    }

    public AppSettingsService(AppSettingsDao appSettingsDao) {
        this.appSettingsDao = appSettingsDao;
    }

    public AppSettings getAppSettings() {
        return appSettingsDao.retrieveSettings();
    }

    public void removeAppConfiguration() {
        appSettingsDao.removeAppSettings();
    }

    public AppSettings save(String configurationsPath, Integer lastConfigurationUsedId, boolean createIfNotExists) {
        String n = (configurationsPath == null) ? "" : configurationsPath.trim();
        if (n.isEmpty()) {
            throw new IllegalArgumentException("Configurations path cannot be empty");
        }

        Path path = Path.of(n).toAbsolutePath();
        if (!Files.exists(path)) {
            if (createIfNotExists) {
                try {
                    Files.createDirectories(path);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to create configurations directory: " + path, e);
                }
            } else {
                throw new IllegalArgumentException("Configurations path does not exist: " + path);
            }
        } else if (!Files.isDirectory(path)) {
            throw new IllegalArgumentException("Configurations path is not a directory: " + path);
        }

        AppSettings settings = new AppSettings();
        settings.setConfigurationsPath(path.toString());
        settings.setLastConfigurationUsedId(lastConfigurationUsedId);
        appSettingsDao.saveSettings(settings);
        return settings;
    }
}
