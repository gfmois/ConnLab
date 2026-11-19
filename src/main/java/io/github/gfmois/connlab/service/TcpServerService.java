package io.github.gfmois.connlab.service;

import io.github.gfmois.connlab.core.response.ResponseEngine;
import io.github.gfmois.connlab.core.server.*;
import io.github.gfmois.connlab.model.Configuration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

public class TcpServerService implements ServerEventListener {
    private static final Logger logger = LogManager.getLogger(TcpServerService.class);
    private final List<ServerEventListener> listeners = new CopyOnWriteArrayList<>();
    private final ResponseEngine responseEngine = new ResponseEngine();
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

    /**
     * Answers the next messages with {@code configuration}, without restarting the server. The
     * host and port the server listens on do not change until it is started again.
     *
     * @return false when the server is not running and nothing changed
     */
    public synchronized boolean reloadConfiguration(Configuration configuration) {
        if (!isRunning() || configuration == null) {
            return false;
        }

        activeConfiguration = configuration;
        logger.info("Reloaded configuration {}", configuration.getName());
        return true;
    }

    /**
     * @return the configuration used to answer messages, or null when the server is not running
     */
    public Configuration getActiveConfiguration() {
        return activeConfiguration;
    }

    public boolean isRunning() {
        TcpServer current = server;
        return current != null && current.isRunning();
    }

    public void sendToClient(String connectionId, String message) {
        TcpServer current = server;
        if (current != null && current.isRunning()) {
            try {
                String address = current.getRemoteAddress(connectionId);
                current.sendToClient(connectionId, message);
                publish(ServerEvent.messageSent(connectionId, address, message));
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

    /**
     * @return the port the server is listening on (useful when started on port 0), or -1 when not running
     */
    public int getLocalPort() {
        TcpServer current = server;
        return current != null ? current.getLocalPort() : -1;
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
        Optional<ResponseEngine.Reply> reply = responseEngine.respond(configuration, incoming);
        if (reply.isEmpty()) {
            return;
        }

        String response = reply.get().text();
        logger.info("Built response '{}' for incoming message '{}'", response, incoming);

        if (reply.get().message().isBroadcast()) {
            currentServer.broadcast(response, true);
        } else {
            try {
                currentServer.sendToClient(event.getConnectionId(), response);
                publish(ServerEvent.messageSent(event.getConnectionId(), event.getRemoteAddress(), response));
            } catch (RetryLimitExceededException e) {
                logger.error("Error while trying to send the message {} to client {}", response, event.getConnectionId(), e);
            }
        }
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
