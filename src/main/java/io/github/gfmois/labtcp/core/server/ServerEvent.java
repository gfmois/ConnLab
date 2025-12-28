package io.github.gfmois.labtcp.core.server;

import java.time.Instant;
import java.util.StringJoiner;

public class ServerEvent {
    private final ServerEventType type;
    private final Instant timestamp;
    private final String connectionId;
    private final String remoteAddress;
    private final String message;
    private final Throwable error;

    public ServerEvent(ServerEventType type, String connectionId, String remoteAddress, String message, Throwable error) {
        this.type = type;
        this.timestamp = Instant.now();
        this.connectionId = connectionId;
        this.remoteAddress = remoteAddress;
        this.message = message;
        this.error = error;
    }

    public static ServerEvent started() {
        return new ServerEvent(ServerEventType.STARTED, null, null, null, null);
    }

    public static ServerEvent stoped() {
        return new ServerEvent(ServerEventType.STOPPED, null, null, null, null);
    }

    public static ServerEvent clientConnected(String connectionId, String remoteAddress) {
        return new ServerEvent(ServerEventType.CLIENT_CONNECTED, connectionId, remoteAddress, null, null);
    }

    public static ServerEvent clientDisconnected(String connectionId, String remoteAddress) {
        return new ServerEvent(ServerEventType.CLIENT_DISCONNECTED, connectionId, remoteAddress, null, null);
    }

    public static ServerEvent messageReceived(String connectionId, String remoteAddress, String message) {
        return new ServerEvent(ServerEventType.MESSAGE_RECEIVED, connectionId, remoteAddress, message, null);
    }

    public static ServerEvent messageSent(String connectionId, String remoteAddress, String message) {
        return new ServerEvent(ServerEventType.MESSAGE_SENT, connectionId, remoteAddress, message, null);
    }

    public static ServerEvent error(Throwable error) {
        return new ServerEvent(ServerEventType.ERROR, null, null, null, error);
    }

    public ServerEventType getType() {
        return type;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getConnectionId() {
        return connectionId;
    }

    public String getRemoteAddress() {
        return remoteAddress;
    }

    public String getMessage() {
        return message;
    }

    public Throwable getError() {
        return error;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", ServerEvent.class.getSimpleName() + "[", "]")
                .add("type=" + type)
                .add("timestamp=" + timestamp)
                .add("connectionId='" + connectionId + "'")
                .add("remoteAddress='" + remoteAddress + "'")
                .add("message='" + message + "'")
                .add("error=" + error)
                .toString();
    }
}
