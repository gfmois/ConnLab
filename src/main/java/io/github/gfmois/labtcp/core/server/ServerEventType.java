package io.github.gfmois.labtcp.core.server;

public enum ServerEventType {
    STARTED,
    STOPPED,
    CLIENT_CONNECTED,
    CLIENT_DISCONNECTED,
    MESSAGE_RECEIVED,
    ERROR
}