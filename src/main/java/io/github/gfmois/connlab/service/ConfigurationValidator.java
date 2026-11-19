package io.github.gfmois.connlab.service;

import io.github.gfmois.connlab.model.Configuration;
import io.github.gfmois.connlab.model.Message;
import io.github.gfmois.connlab.model.response.ResponseMessagePart;
import io.github.gfmois.connlab.model.response.ResponseType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Checks that a configuration can be saved and served.
 */
public final class ConfigurationValidator {
    private ConfigurationValidator() {
    }

    /**
     * @param configuration the configuration to check
     * @param others        the other known configurations, used to reject duplicated names; the
     *                      configuration itself (same file) may be included and is skipped
     * @return a readable message for every problem found, empty when the configuration is valid
     */
    public static List<String> validate(Configuration configuration, Collection<Configuration> others) {
        List<String> errors = new ArrayList<>();
        String name = configuration.getName() == null ? "" : configuration.getName().trim();
        if (name.isEmpty()) {
            errors.add("The name is required");
        } else if (others != null && others.stream().anyMatch(other -> isAnotherWithName(configuration, other, name))) {
            errors.add("There is already a configuration named \"" + name + "\"");
        }

        if (configuration.getPort() < 1 || configuration.getPort() > 65535) {
            errors.add("The port must be a number between 1 and 65535");
        }

        List<Message> messages = configuration.getMessages();
        if (messages != null) {
            for (int i = 0; i < messages.size(); i++) {
                validateMessage(messages.get(i), label(i, messages.get(i)), errors);
            }
        }
        return errors;
    }

    /**
     * @return the number of capturing groups of {@code regex}, or -1 when it is empty or invalid
     */
    public static int groupCount(String regex) {
        if (regex == null || regex.isEmpty()) {
            return -1;
        }
        try {
            return Pattern.compile(regex).matcher("").groupCount();
        } catch (PatternSyntaxException e) {
            return -1;
        }
    }

    /**
     * @return why {@code regex} cannot be used, or null when it is valid
     */
    public static String regexError(String regex) {
        if (regex == null || regex.isEmpty()) {
            return "the received regex is required";
        }
        try {
            Pattern.compile(regex);
            return null;
        } catch (PatternSyntaxException e) {
            return "invalid regex: " + e.getDescription();
        }
    }

    private static boolean isAnotherWithName(Configuration configuration, Configuration other, String name) {
        if (other == configuration) {
            return false;
        }
        if (configuration.getConfigPath() != null && Objects.equals(configuration.getConfigPath(), other.getConfigPath())) {
            return false;
        }
        return other.getName() != null && other.getName().trim().toLowerCase(Locale.ROOT).equals(name.toLowerCase(Locale.ROOT));
    }

    private static String label(int index, Message message) {
        String title = message.getTitle();
        return "Message " + (index + 1) + (title == null || title.isBlank() ? "" : " (" + title.trim() + ")");
    }

    private static void validateMessage(Message message, String label, List<String> errors) {
        String regexError = regexError(message.getReceivedRegex());
        if (regexError != null) {
            errors.add(label + ": " + regexError);
        }

        List<ResponseMessagePart> parts = message.getResponseBuilder();
        if (parts == null || parts.isEmpty()) {
            errors.add(label + ": the response is empty");
            return;
        }

        int groups = groupCount(message.getReceivedRegex());
        for (int i = 0; i < parts.size(); i++) {
            ResponseMessagePart part = parts.get(i);
            String partLabel = label + ", part " + (i + 1);
            if (part.getType() == null) {
                errors.add(partLabel + ": the type is required");
            } else if (part.getType() == ResponseType.MESSAGE_PART) {
                Integer index = part.getPartIndex();
                if (index == null || index < 0) {
                    errors.add(partLabel + ": the group index is required");
                } else if (groups >= 0 && index > groups) {
                    errors.add(partLabel + ": group " + index + " does not exist, the regex has "
                            + (groups == 1 ? "1 group" : groups + " groups"));
                }
            }
        }
    }
}
