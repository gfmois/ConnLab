package io.github.gfmois.connlab.support;

import io.github.gfmois.connlab.model.Configuration;
import io.github.gfmois.connlab.model.Message;
import io.github.gfmois.connlab.model.response.ResponseMessagePart;
import io.github.gfmois.connlab.model.response.ResponseType;

import java.util.ArrayList;
import java.util.List;

/**
 * Builders for configurations in tests.
 */
public final class Configs {
    private Configs() {
    }

    public static ResponseMessagePart text(String text) {
        ResponseMessagePart part = new ResponseMessagePart();
        part.setType(ResponseType.STATIC);
        part.setText(text);
        return part;
    }

    public static ResponseMessagePart group(Integer index) {
        ResponseMessagePart part = new ResponseMessagePart();
        part.setType(ResponseType.MESSAGE_PART);
        part.setPartIndex(index);
        return part;
    }

    public static Message message(String title, String regex, ResponseMessagePart... parts) {
        Message message = new Message();
        message.setTitle(title);
        message.setReceivedRegex(regex);
        message.setResponseBuilder(new ArrayList<>(List.of(parts)));
        return message;
    }

    public static Message broadcast(Message message) {
        message.setBroadcast(true);
        return message;
    }

    public static Configuration configuration(boolean hasToRespond, Message... messages) {
        Configuration configuration = new Configuration();
        configuration.setName("Test");
        configuration.setHasToRespond(hasToRespond);
        configuration.setMessages(new ArrayList<>(List.of(messages)));
        return configuration;
    }

    /**
     * The example of the bundled default.json: {@code Hello, <name>} → {@code Bye, <name>!}.
     */
    public static Message helloMessage() {
        return message("Hello", "^Hello, (.*)$", text("Bye, "), group(1), text("!"));
    }
}
