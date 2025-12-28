package io.github.gfmois.labtcp.core.server;

public abstract class TcpLabException extends RuntimeException {
    protected TcpLabException(String message) {
        super(message);
    }

    protected TcpLabException(String message, Throwable cause) {
        super(message, cause);
    }

    protected TcpLabException(Throwable cause) {
        super(cause);
    }
}