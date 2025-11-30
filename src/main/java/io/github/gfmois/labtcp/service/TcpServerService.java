package io.github.gfmois.labtcp.service;

import io.github.gfmois.labtcp.core.server.ServerEvent;
import io.github.gfmois.labtcp.core.server.ServerEventListener;
import io.github.gfmois.labtcp.core.server.TcpServer;
import io.github.gfmois.labtcp.model.Configuration;
import io.github.gfmois.labtcp.model.Message;
import io.github.gfmois.labtcp.model.response.ResponseMessagePart;
import io.github.gfmois.labtcp.model.response.ResponseType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TcpServerService implements ServerEventListener {
    private static final Logger logger = LogManager.getLogger(TcpServerService.class);
    private final List<ServerEventListener> listeners = new CopyOnWriteArrayList<>();
    private TcpServer server;
    private Configuration activeConfiguration;

    @Override
    public void onServerEvent(ServerEvent event) {
        switch (event.getType()) {
            case MESSAGE_RECEIVED -> handleIncomingMessage(event);
            default -> publish(event);
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

        logger.info("Stopping TCP server");
        server.stop();
        server = null;
        activeConfiguration = null;
    }

    public boolean isRunning() {
        return server != null && server.isRunning();
    }

    public void sendToClient(String connectionId, String message) {
        if (server != null && server.isRunning()) {
            server.sendToClient(connectionId, message);
        }
    }

    public void broadcast(String message) {
        if (server != null && server.isRunning()) {
            server.broadcast(message);
        }
    }

    private void handleIncomingMessage(ServerEvent event) {
        publish(event); // For the UI

        if (activeConfiguration == null) {
            logger.debug("No active configuration, ignoring message");
            return;
        }

        if (!activeConfiguration.isHasToRespond()) {
            logger.debug("Configuration '{}' is set to not respond, ignoring", activeConfiguration.getName());
            return;
        }

        String incoming = event.getMessage();
        if (incoming == null || incoming.isEmpty()) {
            return;
        }

        Message matchedMessage = findMatchingMessage(incoming);
        if (matchedMessage == null) {
            logger.debug("No message pattern matched for incoming: {}", incoming);
            return;
        }

        String response = buildResponse(incoming, matchedMessage);
        if (response == null || response.isEmpty()) {
            logger.debug("Built empty response for incoming: {}", incoming);
            return;
        }

        logger.info("Built response '{}' for incoming message '{}'", response, incoming);

        if (activeConfiguration.isBroadcast()) {
            server.broadcast(response);
        } else {
            server.sendToClient(event.getConnectionId(), response);
        }
    }

    private Message findMatchingMessage(String incoming) {
        List<Message> messages = activeConfiguration.getMessages();
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

            Pattern pattern = Pattern.compile(regex);
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
}
