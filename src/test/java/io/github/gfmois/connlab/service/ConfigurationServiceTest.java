package io.github.gfmois.connlab.service;

import io.github.gfmois.connlab.app.AppSettings;
import io.github.gfmois.connlab.core.response.ResponseEngine;
import io.github.gfmois.connlab.model.Configuration;
import io.github.gfmois.connlab.model.Message;
import io.github.gfmois.connlab.model.response.ResponseType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

class ConfigurationServiceTest {
    @TempDir
    Path configsDir;

    /**
     * A ConfigurationService reading from {@code folder}, without touching the database.
     */
    private static ConfigurationService serviceFor(Path folder) {
        AppSettingsService settings = new AppSettingsService(null) {
            @Override
            public AppSettings getAppSettings() {
                AppSettings appSettings = new AppSettings();
                appSettings.setConfigurationsPath(folder.toString());
                return appSettings;
            }
        };
        return new ConfigurationService(settings);
    }

    private void write(String relativePath, String content) throws IOException {
        Path file = configsDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private static List<String> names(List<Configuration> configurations) {
        return configurations.stream().map(Configuration::getName).sorted().toList();
    }

    private void copyBundledDefault() throws IOException {
        try (InputStream in = Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("default.json"))) {
            Files.copy(in, configsDir.resolve("default.json"));
        }
    }

    @Test
    void parsesTheBundledDefaultConfiguration() throws IOException {
        copyBundledDefault();

        Configuration cfg = serviceFor(configsDir).getAll().getFirst();

        assertEquals("Default", cfg.getName());
        assertEquals(6969, cfg.getPort());
        assertTrue(cfg.isHasToRespond());
        Message message = cfg.getMessages().getFirst();
        assertTrue(message.isBroadcast());
        assertEquals(List.of(ResponseType.STATIC, ResponseType.MESSAGE_PART, ResponseType.STATIC),
                message.getResponseBuilder().stream().map(p -> p.getType()).toList());
    }

    @Test
    void theBundledDefaultConfigurationAnswersItsExample() throws IOException {
        copyBundledDefault();
        Configuration cfg = serviceFor(configsDir).getAll().getFirst();

        String reply = new ResponseEngine().respond(cfg, "Hello, Juan").orElseThrow().text();

        assertEquals("Bye, Juan!", reply);
    }

    @Test
    void loadsEveryJsonFileIncludingSubfolders() throws IOException {
        write("a.json", "{\"name\": \"A\", \"port\": 1000}");
        write("group/b.json", "{\"name\": \"B\", \"port\": 2000}");

        assertEquals(List.of("A", "B"), names(serviceFor(configsDir).getAll()));
    }

    @Test
    void ignoresFilesThatAreNotJson() throws IOException {
        write("a.json", "{\"name\": \"A\"}");
        write("notes.txt", "{\"name\": \"Not a configuration\"}");
        write("a.json.bak", "{\"name\": \"Backup\"}");

        assertEquals(List.of("A"), names(serviceFor(configsDir).getAll()));
    }

    @Test
    void skipsInvalidFilesAndLoadsTheRest() throws IOException {
        write("valid.json", "{\"name\": \"Valid\"}");
        write("broken.json", "{\"name\": ");
        write("unknown-type.json", """
                {"name": "Unknown type", "messages": [
                  {"receivedRegex": ".*", "responseBuilder": [{"type": "nope", "text": "x"}]}
                ]}""");

        assertEquals(List.of("Valid"), names(serviceFor(configsDir).getAll()));
    }

    @Test
    void missingFolderGivesNoConfigurations() {
        assertTrue(serviceFor(configsDir.resolve("missing")).getAll().isEmpty());
    }
}
