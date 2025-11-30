package io.github.gfmois.labtcp.core.server;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.net.Socket;
import java.util.UUID;

public class ClientSession implements Runnable {
    private static final Logger logger = LogManager.getLogger(ClientSession.class);
    private final String id;
    private final Socket socket;
    private final TcpServer server;

    private volatile boolean running = true;
    private PrintWriter out;

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
            this.out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())));

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

    public void send(String message) {
        if (out != null) {
            out.println(message);
        }
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
}
