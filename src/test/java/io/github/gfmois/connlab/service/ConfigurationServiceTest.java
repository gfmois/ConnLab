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

import static io.github.gfmois.connlab.support.Configs.*;
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

    @Test
    void configurationsAreSortedByNameAndKnowTheirFile() throws IOException {
        write("z.json", "{\"name\": \"beta\", \"port\": 1}");
        write("a.json", "{\"name\": \"Alpha\", \"port\": 1}");

        List<Configuration> all = serviceFor(configsDir).getAll();

        assertEquals(List.of("Alpha", "beta"), all.stream().map(Configuration::getName).toList());
        assertEquals(configsDir.resolve("a.json").toAbsolutePath().toString(), all.getFirst().getConfigPath());
    }

    // ----- Saving -----

    @Test
    void savingWritesTheChangesToTheSameFile() throws IOException {
        copyBundledDefault();
        ConfigurationService service = serviceFor(configsDir);
        Configuration cfg = service.getAll().getFirst();

        cfg.setPort(7000);
        cfg.getMessages().getFirst().setReceivedRegex("^Hi, (.*)$");
        service.save(cfg);

        List<Configuration> reloaded = service.getAll();
        assertEquals(1, reloaded.size());
        assertEquals(7000, reloaded.getFirst().getPort());
        assertEquals("Bye, Ana!", new ResponseEngine().respond(reloaded.getFirst(), "Hi, Ana").orElseThrow().text());
    }

    @Test
    void savingAnUnchangedFileKeepsItsContent() throws IOException {
        copyBundledDefault();
        ConfigurationService service = serviceFor(configsDir);
        String before = Files.readString(configsDir.resolve("default.json"));

        service.save(service.getAll().getFirst());

        assertEquals(normalizeNewLines(before).strip(), normalizeNewLines(Files.readString(configsDir.resolve("default.json"))).strip());
    }

    @Test
    void theFileOnlyHasTheFieldsThatApply() throws IOException {
        ConfigurationService service = serviceFor(configsDir);
        Configuration cfg = configuration(true, message("Hello", "^Hello, (.*)$", text("Bye, "), group(1)));
        cfg.setName("  Clean  ");
        cfg.setPort(1234);
        cfg.setConfigPath(null);
        // Leftovers of switching a part between types in the editor
        cfg.getMessages().getFirst().getResponseBuilder().get(0).setPartIndex(3);
        cfg.getMessages().getFirst().getResponseBuilder().get(1).setText("ignored");

        Configuration saved = service.save(cfg);

        String json = normalizeNewLines(Files.readString(Path.of(saved.getConfigPath())));
        assertEquals("""
                {
                    "name": "Clean",
                    "port": 1234,
                    "hasToRespond": true,
                    "messages": [
                        {
                            "title": "Hello",
                            "broadcast": false,
                            "receivedRegex": "^Hello, (.*)$",
                            "responseBuilder": [
                                {
                                    "type": "static",
                                    "text": "Bye, "
                                },
                                {
                                    "type": "messagePart",
                                    "partIndex": 1
                                }
                            ]
                        }
                    ]
                }
                """, json);
    }

    @Test
    void savingANewConfigurationCreatesAFileNamedAfterIt() {
        ConfigurationService service = serviceFor(configsDir);

        Configuration first = service.create("Impresora Zebra");
        Configuration second = service.duplicate(first, "Impresora zebra!");

        assertEquals(configsDir.resolve("impresora-zebra.json").toAbsolutePath().toString(), first.getConfigPath());
        assertEquals(configsDir.resolve("impresora-zebra-2.json").toAbsolutePath().toString(), second.getConfigPath());
        assertEquals(List.of("Impresora Zebra", "Impresora zebra!"), names(service.getAll()));
    }

    @Test
    void createdConfigurationsHaveUsableDefaults() {
        Configuration created = serviceFor(configsDir).create("New");

        assertEquals(6969, created.getPort());
        assertTrue(created.isHasToRespond());
        assertTrue(created.getMessages().isEmpty());
    }

    @Test
    void invalidConfigurationsAreNotWritten() throws IOException {
        write("a.json", "{\"name\": \"Taken\", \"port\": 1}");
        ConfigurationService service = serviceFor(configsDir);

        var e = assertThrows(ConfigurationService.InvalidConfigurationException.class, () -> service.create("taken"));

        assertEquals(List.of("There is already a configuration named \"taken\""), e.getErrors());
        try (var files = Files.list(configsDir)) {
            assertEquals(1, files.count());
        }
    }

    @Test
    void savingDoesNotChangeTheGivenConfiguration() {
        ConfigurationService service = serviceFor(configsDir);
        Configuration cfg = configuration(true);
        cfg.setName("  Spaces  ");
        cfg.setPort(1);

        service.save(cfg);

        assertEquals("  Spaces  ", cfg.getName());
        assertNull(cfg.getConfigPath());
    }

    @Test
    void deleteRemovesTheFile() {
        ConfigurationService service = serviceFor(configsDir);
        Configuration created = service.create("To delete");

        service.delete(created);

        assertTrue(service.getAll().isEmpty());
    }

    @Test
    void copiesAreIndependent() {
        ConfigurationService service = serviceFor(configsDir);
        Configuration cfg = configuration(true, helloMessage());
        cfg.setConfigPath("/configs/x.json");

        Configuration copy = service.copy(cfg);
        copy.getMessages().getFirst().getResponseBuilder().getFirst().setText("Changed");
        copy.getMessages().add(helloMessage());

        assertEquals("/configs/x.json", copy.getConfigPath());
        assertEquals(1, cfg.getMessages().size());
        assertEquals("Bye, ", cfg.getMessages().getFirst().getResponseBuilder().getFirst().getText());
    }

    @Test
    void toJsonIgnoresChangesThatAreNotSaved() {
        ConfigurationService service = serviceFor(configsDir);
        Configuration cfg = configuration(true, helloMessage());
        String json = service.toJson(cfg);

        Configuration sameButUntrimmed = service.copy(cfg);
        sameButUntrimmed.setName(" Test ");
        sameButUntrimmed.setConfigPath("/elsewhere.json");
        Configuration different = service.copy(cfg);
        different.setPort(99);

        assertEquals(json, service.toJson(sameButUntrimmed));
        assertNotEquals(json, service.toJson(different));
    }

    @Test
    void fileNamesAreSafe() {
        assertEquals("hola-que-tal", ConfigurationService.fileNameFor("¡Hola, qué tal!"));
        assertEquals("a-b", ConfigurationService.fileNameFor("  A / B  "));
        assertEquals("configuration", ConfigurationService.fileNameFor("***"));
    }

    private static String normalizeNewLines(String text) {
        return text.replace("\r\n", "\n");
    }
}
