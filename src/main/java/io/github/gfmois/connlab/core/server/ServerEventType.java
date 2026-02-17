package io.github.gfmois.connlab.core.server;

public enum ServerEventType {
    STARTED,
    STOPPED,
    CLIENT_CONNECTED,
    CLIENT_DISCONNECTED,
    MESSAGE_RECEIVED,
    MESSAGE_SENT,
    ERROR
}