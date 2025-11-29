package io.github.gfmois.labtcp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.gfmois.labtcp.app.AppSettings;
import io.github.gfmois.labtcp.model.Configuration;
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
    private final AppSettings appSettings;

    public ConfigurationService() {
        this.appSettings = new AppSettings();
    }

    public ConfigurationService(AppSettings appSettings) {
        this.appSettings = appSettings;
    }

    public List<Configuration> getAll() {
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

        return configurations;
    }
}
