package io.github.gfmois.connlab.service;

import io.github.gfmois.connlab.model.Configuration;
import io.github.gfmois.connlab.model.response.ResponseMessagePart;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.github.gfmois.connlab.support.Configs.*;
import static org.junit.jupiter.api.Assertions.*;

class ConfigurationValidatorTest {

    private static Configuration valid() {
        Configuration configuration = configuration(true, helloMessage());
        configuration.setPort(6969);
        configuration.setConfigPath("/configs/test.json");
        return configuration;
    }

    private static Configuration named(String name, String path) {
        Configuration configuration = valid();
        configuration.setName(name);
        configuration.setConfigPath(path);
        return configuration;
    }

    private static List<String> errorsOf(Configuration configuration) {
        return ConfigurationValidator.validate(configuration, List.of());
    }

    @Test
    void aCompleteConfigurationIsValid() {
        assertEquals(List.of(), errorsOf(valid()));
    }

    @Test
    void aConfigurationWithoutMessagesIsValid() {
        Configuration configuration = valid();
        configuration.getMessages().clear();

        assertEquals(List.of(), errorsOf(configuration));
    }

    @Test
    void theNameIsRequired() {
        Configuration configuration = valid();
        configuration.setName("   ");

        assertEquals(List.of("The name is required"), errorsOf(configuration));
    }

    @Test
    void namesMustBeUniqueIgnoringCase() {
        Configuration configuration = named("Printer", "/configs/a.json");

        List<String> errors = ConfigurationValidator.validate(configuration, List.of(named(" printer ", "/configs/b.json")));

        assertEquals(List.of("There is already a configuration named \"Printer\""), errors);
    }

    @Test
    void theSameFileIsNotADuplicate() {
        Configuration configuration = named("Printer", "/configs/a.json");

        assertEquals(List.of(), ConfigurationValidator.validate(configuration, List.of(named("Printer", "/configs/a.json"))));
    }

    @Test
    void theConfigurationItselfIsNotADuplicate() {
        Configuration configuration = named("Printer", null);

        assertEquals(List.of(), ConfigurationValidator.validate(configuration, List.of(configuration)));
    }

    @Test
    void thePortMustBeInRange() {
        for (int port : new int[]{-1, 0, 65536}) {
            Configuration configuration = valid();
            configuration.setPort(port);

            assertEquals(List.of("The port must be a number between 1 and 65535"), errorsOf(configuration), "port " + port);
        }
    }

    @Test
    void theRegexIsRequiredAndMustCompile() {
        Configuration configuration = configuration(true,
                message("Empty", "", text("x")),
                message(null, "(unclosed", text("x")));
        configuration.setPort(1);

        List<String> errors = errorsOf(configuration);

        assertEquals(2, errors.size());
        assertEquals("Message 1 (Empty): the received regex is required", errors.get(0));
        assertTrue(errors.get(1).startsWith("Message 2: invalid regex"), errors.get(1));
    }

    @Test
    void theResponseCannotBeEmpty() {
        Configuration configuration = configuration(true, message("Silent", "^x$"));
        configuration.setPort(1);

        assertEquals(List.of("Message 1 (Silent): the response is empty"), errorsOf(configuration));
    }

    @Test
    void groupsMustExistInTheRegex() {
        Configuration configuration = configuration(true,
                message("Hello", "^Hello, (.*)$", group(0), group(1), group(2)));
        configuration.setPort(1);

        assertEquals(List.of("Message 1 (Hello), part 3: group 2 does not exist, the regex has 1 group"), errorsOf(configuration));
    }

    @Test
    void groupPartsNeedAnIndexAndEveryPartNeedsAType() {
        ResponseMessagePart withoutType = new ResponseMessagePart();
        Configuration configuration = configuration(true, message("M", "^(a)$", group(null), withoutType));
        configuration.setPort(1);

        assertEquals(List.of(
                "Message 1 (M), part 1: the group index is required",
                "Message 1 (M), part 2: the type is required"), errorsOf(configuration));
    }

    @Test
    void groupCountAndRegexError() {
        assertEquals(2, ConfigurationValidator.groupCount("(a)(b)"));
        assertEquals(0, ConfigurationValidator.groupCount("abc"));
        assertEquals(-1, ConfigurationValidator.groupCount("("));
        assertEquals(-1, ConfigurationValidator.groupCount(""));

        assertNull(ConfigurationValidator.regexError("^ok$"));
        assertNotNull(ConfigurationValidator.regexError("["));
        assertNotNull(ConfigurationValidator.regexError(null));
    }
}
