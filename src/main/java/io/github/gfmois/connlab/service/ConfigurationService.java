package io.github.gfmois.connlab.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.gfmois.connlab.app.AppSettings;
import io.github.gfmois.connlab.model.Configuration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class ConfigurationService {
    private static final Logger logger = LogManager.getLogger(ConfigurationService.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private final AppSettingsService appSettingsService;

    public ConfigurationService() {
        this(new AppSettingsService());
    }

    public ConfigurationService(AppSettingsService appSettingsService) {
        this.appSettingsService = appSettingsService;
    }

    public List<Configuration> getAll() {
        AppSettings appSettings = appSettingsService.getAppSettings();
        logger.info("Using configurations path: {}", appSettings.getConfigurationsPath());
        List<Configuration> configurations = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(Paths.get(appSettings.getConfigurationsPath()))) {
            paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".json"))
                    .forEach(path -> {
                        try {
                            logger.debug("Found file {} in configurations folder {}", path.getFileName(), appSettings.getConfigurationsPath());
                            Configuration configuration = mapper.readValue(path.toFile(), Configuration.class);
                            logger.debug("Parsed file {} to configuration {}", path.getFileName(), configuration);
                            configurations.add(configuration);
                        } catch (IOException e) {
                            logger.error("Could not parse JSON in file {}", path, e);
                        }
                    });
        } catch (IOException e) {
            logger.error("Error while trying to read configurations folder", e);
        }

        logger.debug("Returning: {}", configurations);
        return configurations;
    }
}
