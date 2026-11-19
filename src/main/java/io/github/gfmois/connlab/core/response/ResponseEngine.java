package io.github.gfmois.connlab.core.response;

import io.github.gfmois.connlab.model.Configuration;
import io.github.gfmois.connlab.model.Message;
import io.github.gfmois.connlab.model.response.ResponseMessagePart;
import io.github.gfmois.connlab.model.response.ResponseType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Builds the automatic response of a configuration for an incoming message.
 * <p>
 * The first message whose {@code receivedRegex} fully matches the incoming text wins; its
 * {@code responseBuilder} parts are concatenated, {@code static} parts as literal text and
 * {@code messagePart} parts as the regex group with that index.
 */
public class ResponseEngine {
    private static final Logger logger = LogManager.getLogger(ResponseEngine.class);

    /**
     * A response built for an incoming message.
     *
     * @param message the configuration message whose regex matched
     * @param text    the response to send
     */
    public record Reply(Message message, String text) {
    }

    /**
     * @return the reply for {@code incoming}, or empty when no message matches or the matching
     * message builds an empty response
     */
    public Optional<Reply> respond(Configuration configuration, String incoming) {
        if (configuration == null || incoming == null || incoming.isEmpty()) {
            return Optional.empty();
        }

        List<Message> messages = configuration.getMessages();
        if (messages == null || messages.isEmpty()) {
            logger.debug("Configuration '{}' does not have messages", configuration.getName());
            return Optional.empty();
        }

        for (Message message : messages) {
            Matcher matcher = match(message, incoming);
            if (matcher == null) {
                continue;
            }

            String text = build(message, matcher);
            if (text.isEmpty()) {
                logger.debug("Built empty response for incoming: {}", incoming);
                return Optional.empty();
            }
            return Optional.of(new Reply(message, text));
        }

        logger.debug("No message pattern matched for incoming: {}", incoming);
        return Optional.empty();
    }

    /**
     * @return a matcher positioned on a full match, or null when the regex is missing, invalid or does not match
     */
    private Matcher match(Message message, String incoming) {
        String regex = message.getReceivedRegex();
        if (regex == null || regex.isEmpty()) {
            logger.debug("Message '{}' has no receivedRegex, ignoring it", message.getTitle());
            return null;
        }

        Pattern pattern;
        try {
            pattern = Pattern.compile(regex);
        } catch (PatternSyntaxException e) {
            logger.error("Invalid regex '{}' in message '{}', skipping it: {}", regex, message.getTitle(), e.getDescription());
            return null;
        }

        Matcher matcher = pattern.matcher(incoming);
        if (!matcher.matches()) {
            return null;
        }

        logger.debug("Incoming '{}' matched regex '{}'", incoming, regex);
        return matcher;
    }

    private String build(Message message, Matcher matcher) {
        List<ResponseMessagePart> parts = message.getResponseBuilder();
        if (parts == null || parts.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (ResponseMessagePart part : parts) {
            if (part.getType() == ResponseType.STATIC) {
                String text = part.getText();
                if (text != null) sb.append(text);
            } else if (part.getType() == ResponseType.MESSAGE_PART) {
                Integer idx = part.getPartIndex();
                if (idx != null && idx >= 0 && idx <= matcher.groupCount()) {
                    String group = matcher.group(idx);
                    if (group != null) sb.append(group);
                } else {
                    logger.warn("Invalid partIndex '{}' for message regex '{}'", idx, message.getReceivedRegex());
                }
            }
        }

        return sb.toString();
    }
}
