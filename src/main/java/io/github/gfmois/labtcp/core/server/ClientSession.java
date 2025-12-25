package io.github.gfmois.labtcp.core.server;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.net.Socket;
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
    private OutputStream out;

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
        return socket.getRemoteSocketAddress().toString();
    }

    @Override
    public void run() {
        logger.debug("Client session {} started", id);
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            this.out = socket.getOutputStream();

            String line;
            while (running && (line = in.readLine()) != null) {
                logger.debug("Received from {}: {}", id, line);
                server.onMessageReceived(this, line);
            }
        } catch (IOException e) {
            logger.error("Client session {} ended with exception", id, e);
        } finally {
            running = false;
            server.onClientDisconnected(this);
            closeSocket();
        }
    }

    public void send(String message) throws IOException {
        if (out == null) {
            throw new IOException("No OutputStream");
        }

        byte[] data = (message + "\r\n").getBytes(StandardCharsets.UTF_8);
        out.write(data);
        out.flush();
    }

    public void stop() {
        running = false;
        closeSocket();
    }

    private void closeSocket() {
        try {
            socket.close();
        } catch (IOException ignored) {
            logger.debug("Client session {} closed", id);
        }
    }

    public void close() {
        if (!closed.compareAndExchange(false, true)) {
            return; // Already closed
        }

        running = false;

        try {
            socket.close();
        } catch (IOException e) {
            logger.debug("Error closing client session: {}", id, e);
        }
    }
}
