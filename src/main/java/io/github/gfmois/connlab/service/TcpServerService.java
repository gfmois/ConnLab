package io.github.gfmois.connlab.service;

import io.github.gfmois.connlab.core.server.*;
import io.github.gfmois.connlab.model.Configuration;
import io.github.gfmois.connlab.model.Message;
import io.github.gfmois.connlab.model.response.ResponseMessagePart;
import io.github.gfmois.connlab.model.response.ResponseType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class TcpServerService implements ServerEventListener {
    private static final Logger logger = LogManager.getLogger(TcpServerService.class);
    private final List<ServerEventListener> listeners = new CopyOnWriteArrayList<>();
    private volatile TcpServer server;
    private volatile Configuration activeConfiguration;

    @Override
    public void onServerEvent(ServerEvent event) {
        if (Objects.requireNonNull(event.getType()) == ServerEventType.MESSAGE_RECEIVED) {
            handleIncomingMessage(event);
        } else {
            publish(event);
        }
    }

    public void addListener(ServerEventListener listener) {
        listeners.add(listener);
    }

    public void removeListener(ServerEventListener listener) {
        listeners.remove(listener);
    }

    public void publish(ServerEvent event) {
        for (ServerEventListener listener : listeners) {
            try {
                listener.onServerEvent(event);
            } catch (Exception e) {
                logger.warn("Listener threw exception: {}", e.toString());
            }
        }
    }

    public synchronized void start(String host, int port, Configuration cnf) throws IOException {
        if (server != null && server.isRunning()) {
            stop();
        }

        this.activeConfiguration = cnf;
        this.server = new TcpServer(host, port);
        this.server.addListener(this);

        logger.info("Starting TCP server on {}:{} with configuration {}", host, port, cnf.getName());
        server.start();
    }

    public synchronized void stop() {
        if (server == null || !server.isRunning()) {
            return;
        }

        server.stop();
        server = null;
        activeConfiguration = null;
    }

    public boolean isRunning() {
        TcpServer current = server;
        return current != null && current.isRunning();
    }

    public void sendToClient(String connectionId, String message) {
        TcpServer current = server;
        if (current != null && current.isRunning()) {
            try {
                current.sendToClient(connectionId, message);
            } catch (RetryLimitExceededException e) {
                logger.error("Error while sending the message {} to client {}", message, connectionId);
            }
        }
    }

    public void broadcast(String message, boolean showLog) {
        TcpServer current = server;
        if (current != null && current.isRunning()) {
            current.broadcast(message, showLog);
        }
    }

    public void broadcast(String message) {
        broadcast(message, true);
    }

    public long getUptimeMillis() {
        TcpServer current = server;
        return current != null ? current.getUptimeMillis() : 0;
    }

    private void handleIncomingMessage(ServerEvent event) {
        publish(event); // For the UI

        // Snapshot both fields: stop() may null them from another thread while we are handling the message
        TcpServer currentServer = server;
        Configuration configuration = activeConfiguration;
        if (currentServer == null || configuration == null) {
            logger.debug("No active configuration, ignoring message");
            return;
        }

        if (!configuration.isHasToRespond()) {
            logger.debug("Configuration '{}' is set to not respond, ignoring", configuration.getName());
            return;
        }

        String incoming = event.getMessage();
        if (incoming == null || incoming.isEmpty()) {
            return;
        }

        Message matchedMessage = findMatchingMessage(configuration, incoming);
        if (matchedMessage == null) {
            logger.debug("No message pattern matched for incoming: {}", incoming);
            return;
        }

        String response = buildResponse(incoming, matchedMessage);
        if (response.isEmpty()) {
            logger.debug("Built empty response for incoming: {}", incoming);
            return;
        }

        logger.info("Built response '{}' for incoming message '{}'", response, incoming);

        if (matchedMessage.isBroadcast()) {
            currentServer.broadcast(response, false);
        } else {
            try {
                currentServer.sendToClient(event.getConnectionId(), response);
            } catch (RetryLimitExceededException e) {
                logger.error("Error while trying to send the message {} to client {}", response, event.getConnectionId(), e);
            }
        }
    }

    private Message findMatchingMessage(Configuration configuration, String incoming) {
        List<Message> messages = configuration.getMessages();
        if (messages == null || messages.isEmpty()) {
            logger.info("Active configuration does not have messages");
            return null;
        }

        for (Message msg : messages) {
            String regex = msg.getReceivedRegex();
            if (regex == null || regex.isEmpty()) {
                logger.info("Received Regex is empty or null, ignoring...");
                continue;
            }

            Pattern pattern;
            try {
                pattern = Pattern.compile(regex);
            } catch (PatternSyntaxException e) {
                logger.error("Invalid regex '{}' in message '{}', skipping it: {}", regex, msg.getTitle(), e.getDescription());
                continue;
            }

            Matcher matcher = pattern.matcher(incoming);
            if (matcher.matches()) {
                logger.debug("Incoming '{}' matched regex '{}' for message", incoming, regex);
                return msg;
            }
        }

        return null;
    }

    private String buildResponse(String incoming, Message message) {
        List<ResponseMessagePart> parts = message.getResponseBuilder();
        if (parts == null || parts.isEmpty()) {
            return "";
        }

        String regex = message.getReceivedRegex();
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(incoming);
        if (!matcher.matches()) {
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
                    logger.warn("Invalid partIndex '{}' for message regex '{}'", idx, regex);
                }
            }
        }

        return sb.toString();
    }

    public void kickClient(String address) {
        if (address == null || address.isBlank()) {
            logger.warn("Cannot kick client due address is null or blank");
            return;
        }

        TcpServer current = server;
        if (current != null) {
            current.removeClient(address);
        }
    }
}
