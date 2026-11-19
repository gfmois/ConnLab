package io.github.gfmois.connlab.service;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.core.util.Separators;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import io.github.gfmois.connlab.app.AppSettings;
import io.github.gfmois.connlab.model.Configuration;
import io.github.gfmois.connlab.model.Message;
import io.github.gfmois.connlab.model.response.ResponseMessagePart;
import io.github.gfmois.connlab.model.response.ResponseType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public class ConfigurationService {
    private static final Logger logger = LogManager.getLogger(ConfigurationService.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final ObjectWriter writer = mapper.writer(prettyPrinter());
    private static final int DEFAULT_PORT = 6969;
    private final AppSettingsService appSettingsService;

    public ConfigurationService() {
        this(new AppSettingsService());
    }

    public ConfigurationService(AppSettingsService appSettingsService) {
        this.appSettingsService = appSettingsService;
    }

    /**
     * Reads every {@code .json} file of the configurations folder and its subfolders, sorted by
     * name. Files that cannot be parsed are logged and skipped.
     */
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
                            configuration.setConfigPath(path.toAbsolutePath().toString());
                            logger.debug("Parsed file {} to configuration {}", path.getFileName(), configuration);
                            configurations.add(configuration);
                        } catch (IOException e) {
                            logger.error("Could not parse JSON in file {}", path, e);
                        }
                    });
        } catch (IOException e) {
            logger.error("Error while trying to read configurations folder", e);
        }

        configurations.sort(Comparator.comparing(c -> c.getName() == null ? "" : c.getName(), String.CASE_INSENSITIVE_ORDER));
        logger.debug("Returning: {}", configurations);
        return configurations;
    }

    /**
     * Writes {@code configuration} to its file, or to a new file of the configurations folder
     * named after it when it does not have one yet.
     *
     * @return the configuration as written, with its file set
     * @throws InvalidConfigurationException when the configuration is not valid; nothing is written
     * @throws UncheckedIOException          when the file cannot be written
     */
    public Configuration save(Configuration configuration) {
        Configuration toSave = copy(configuration);
        normalize(toSave);

        List<String> errors = ConfigurationValidator.validate(toSave, getAll());
        if (!errors.isEmpty()) {
            throw new InvalidConfigurationException(errors);
        }

        Path file = toSave.getConfigPath() != null ? Path.of(toSave.getConfigPath()) : newFileFor(toSave.getName());
        toSave.setConfigPath(file.toAbsolutePath().toString());
        try {
            write(toSave, file);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write " + file, e);
        }

        logger.info("Saved configuration '{}' to {}", toSave.getName(), file);
        return toSave;
    }

    /**
     * Creates and saves an empty configuration named {@code name}.
     */
    public Configuration create(String name) {
        Configuration configuration = new Configuration();
        configuration.setName(name == null ? null : name.trim());
        configuration.setPort(DEFAULT_PORT);
        configuration.setHasToRespond(true);
        configuration.setMessages(new ArrayList<>());
        return save(configuration);
    }

    /**
     * Saves a copy of {@code configuration} in a new file, named {@code newName}.
     */
    public Configuration duplicate(Configuration configuration, String newName) {
        Configuration duplicated = copy(configuration);
        duplicated.setConfigPath(null);
        duplicated.setName(newName == null ? null : newName.trim());
        return save(duplicated);
    }

    /**
     * Deletes the file of {@code configuration}.
     *
     * @throws UncheckedIOException when the file cannot be deleted
     */
    public void delete(Configuration configuration) {
        if (configuration.getConfigPath() == null) {
            return;
        }

        Path file = Path.of(configuration.getConfigPath());
        try {
            Files.deleteIfExists(file);
            logger.info("Deleted configuration '{}' ({})", configuration.getName(), file);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not delete " + file, e);
        }
    }

    /**
     * @return a deep copy of {@code configuration}, keeping its file
     */
    public Configuration copy(Configuration configuration) {
        Configuration copy = mapper.convertValue(configuration, Configuration.class);
        copy.setId(configuration.getId());
        copy.setConfigPath(configuration.getConfigPath());
        if (copy.getMessages() == null) {
            copy.setMessages(new ArrayList<>());
        }
        for (Message message : copy.getMessages()) {
            if (message.getResponseBuilder() == null) {
                message.setResponseBuilder(new ArrayList<>());
            }
        }
        return copy;
    }

    /**
     * @return {@code configuration} as it would be written to its file
     */
    public String toJson(Configuration configuration) {
        Configuration normalized = copy(configuration);
        normalize(normalized);
        try {
            return writer.writeValueAsString(normalized);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Trims the texts that are not part of a response and drops the fields that do not apply to
     * each response part, so the file only keeps what is used.
     */
    private static void normalize(Configuration configuration) {
        configuration.setName(trimToNull(configuration.getName()));
        configuration.setDescription(trimToNull(configuration.getDescription()));
        configuration.setHost(trimToNull(configuration.getHost()));
        for (Message message : configuration.getMessages()) {
            message.setTitle(trimToNull(message.getTitle()));
            for (ResponseMessagePart part : message.getResponseBuilder()) {
                if (part.getType() == ResponseType.STATIC) {
                    part.setPartIndex(null);
                    if (part.getText() == null) part.setText("");
                } else if (part.getType() == ResponseType.MESSAGE_PART) {
                    part.setText(null);
                }
            }
        }
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Path newFileFor(String name) {
        Path folder = Path.of(appSettingsService.getAppSettings().getConfigurationsPath());
        String base = fileNameFor(name);
        Path file = folder.resolve(base + ".json");
        for (int i = 2; Files.exists(file); i++) {
            file = folder.resolve(base + "-" + i + ".json");
        }
        return file;
    }

    /**
     * @return a file name for a configuration called {@code name}: lower case ASCII letters,
     * digits and dashes
     */
    static String fileNameFor(String name) {
        String ascii = Normalizer.normalize(name == null ? "" : name, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String slug = ascii.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
        return slug.isEmpty() ? "configuration" : slug;
    }

    /**
     * Writes to a temporary file next to {@code file} and then replaces it, so a failure never
     * leaves a half written configuration.
     */
    private static void write(Configuration configuration, Path file) throws IOException {
        Path folder = file.toAbsolutePath().getParent();
        Files.createDirectories(folder);
        Path temp = Files.createTempFile(folder, ".connlab-", ".tmp");
        try {
            Files.writeString(temp, writer.writeValueAsString(configuration) + System.lineSeparator());
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    /**
     * Pretty printing with 4 spaces, one array element per line and no space before colons, the
     * format of the bundled {@code default.json}.
     */
    private static DefaultPrettyPrinter prettyPrinter() {
        DefaultIndenter indenter = new DefaultIndenter("    ", System.lineSeparator());
        DefaultPrettyPrinter printer = new DefaultPrettyPrinter()
                .withSeparators(Separators.createDefaultInstance().withObjectFieldValueSpacing(Separators.Spacing.AFTER));
        printer.indentObjectsWith(indenter);
        printer.indentArraysWith(indenter);
        return printer;
    }

    /**
     * A configuration that cannot be saved; {@link #getErrors()} says why.
     */
    public static class InvalidConfigurationException extends IllegalArgumentException {
        private final List<String> errors;

        public InvalidConfigurationException(List<String> errors) {
            super(String.join("\n", errors));
            this.errors = List.copyOf(errors);
        }

        public List<String> getErrors() {
            return errors;
        }
    }
}
