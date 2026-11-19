package io.github.gfmois.connlab.core.server;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class ClientSession implements Runnable {
    private static final Logger logger = LogManager.getLogger(ClientSession.class);
    private final String id;
    private final Socket socket;
    private final TcpServer server;

    private final AtomicBoolean closed = new AtomicBoolean(false);

    private volatile boolean running = true;

    private BufferedReader in;
    private OutputStream out;

    private final Object ioLock = new Object();

    public ClientSession(Socket socket, TcpServer tcpServer) {
        this.id = UUID.randomUUID().toString();
        this.socket = socket;
        this.server = tcpServer;
    }

    public String getId() {
        return id;
    }

    public TcpServer getServer() {
        return server;
    }

    public String getRemoteAddress() {
        return String.valueOf(socket.getRemoteSocketAddress());
    }

    @Override
    public void run() {
        logger.debug("Client session {} started", id);

        try {
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            this.out = socket.getOutputStream();

            String line;
            while (running && (line = in.readLine()) != null) {
                logger.debug("Received from {}: {}", id, line);
                server.onMessageReceived(this, line);
            }

            logger.debug("Client {} EOF / remote closed ({})", id, getRemoteAddress());
        } catch (IOException e) {
            if (isClosingExpected(e)) {
                logger.debug("Client session {} stopped ({})", id, getRemoteAddress());
            } else {
                logger.error("Client session {} I/O failure ({})", id, getRemoteAddress(), e);
            }
        } finally {
            shutdown("run() finally");
        }
    }

    public void send(String message) throws IOException {
        if (message == null) throw new IllegalArgumentException("message == null");
        if (closed.get()) throw new IOException("Session is closed");

        byte[] data = (message + "\r\n").getBytes(StandardCharsets.UTF_8);

        synchronized (ioLock) {
            if (closed.get()) throw new IOException("Session is closed");
            if (out == null) throw new IOException("OutputStream not initialized yet");
            out.write(data);
            out.flush();
        }
    }

    public void stop() {
        shutdown("stop()");
    }

    public boolean close() {
        return shutdown("close()");
    }

    private boolean shutdown(String reason) {
        if (!closed.compareAndExchange(false, true)) {
            return false;
        }

        running = false;
        try {
            server.onClientDisconnected(this);
        } catch (RuntimeException ex) {
            logger.error("onClientDisconnected crashed for {} ({})", id, getRemoteAddress(), ex);
        }

        synchronized (ioLock) {
            try {
                socket.shutdownInput();
            } catch (IOException ex) {
                logger.debug("error while trying to closed input for {} ({})", id, getRemoteAddress(), ex);
            }
            try {
                socket.shutdownOutput();
            } catch (IOException ex) {
                logger.debug("error while trying to close output for {} ({})", id, getRemoteAddress(), ex);
            }

            try {
                socket.close();
                logger.debug("Client session {} closed ({}), reason={}", id, getRemoteAddress(), reason);
            } catch (IOException e) {
                logger.debug("Error closing socket for {} ({}): {}", id, getRemoteAddress(), e.toString());
            } finally {
                in = null;
                out = null;
            }
        }

        return true;
    }

    private boolean isClosingExpected(IOException e) {
        if (!running || closed.get() || socket.isClosed()) return true;
        if (e instanceof SocketException) return true;
        String msg = e.getMessage();
        return msg != null && msg.toLowerCase().contains("socket closed");
    }
}
