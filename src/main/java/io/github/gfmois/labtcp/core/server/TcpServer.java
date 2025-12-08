package io.github.gfmois.labtcp.core.server;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public class TcpServer {
    private static final Logger logger = LogManager.getLogger(TcpServer.class);
    private final String host;
    private final int port;
    private volatile boolean running = false;
    private long startTime;

    private ServerSocket serverSocket;
    private Thread acceptThread;

    private final List<ServerEventListener> listeners = new CopyOnWriteArrayList<>();
    private final Map<String, ClientSession> clients = Collections.synchronizedMap(new HashMap<>());

    public TcpServer(String host, int port) {
        this.host = host;
        this.port = port;
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
                logger.warn("Listener threw exception", e);
            }
        }
    }

    public synchronized void start() throws IOException {
        if (running) return;

        serverSocket = new ServerSocket(port);
        running = true;
        startTime = System.nanoTime();

        logger.info("TCP Server started on port: {}", port);
        publish(ServerEvent.started());

        acceptThread = new Thread(this::acceptLoop, "tcp-accept-thread");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    public synchronized void stop() {
        if (!running) return;
        running = false;
        startTime = 0;

        logger.info("Stopping TCP Server...");

        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException ignored) {
        }

        synchronized (clients) {
            for (ClientSession client : clients.values()) {
                client.stop();
            }

            clients.clear();
        }

        publish(ServerEvent.stoped());
    }

    private void acceptLoop() {
        try {
            while (running) {
                Socket socket = serverSocket.accept();
                ClientSession session = new ClientSession(socket, this);
                clients.put(session.getId(), session);
                publish(ServerEvent.clientConnected(session.getId(), session.getRemoteAddress()));
                Thread t = new Thread(session, "client-" + session.getId());
                t.setDaemon(true);
                t.start();
            }
        } catch (IOException e) {
            if (running) {
                logger.error("Error in accept loop", e);
                publish(ServerEvent.error(e));
            }
        } finally {
            logger.info("Accept loop finished");
        }
    }

    public void onMessageReceived(ClientSession session, String message) {
        publish(ServerEvent.messageReceived(session.getId(), session.getRemoteAddress(), message));
    }

    public void onClientDisconnected(ClientSession session) {
        clients.remove(session.getId());
        publish(ServerEvent.clientDisconnected(session.getId(), session.getRemoteAddress()));
    }

    public void sendToClient(String connectionId, String message) {
        ClientSession session = clients.get(connectionId);
        if (session == null) {
            logger.warn("Could not find the session for {} id", connectionId);
            return;
        }
        session.send(message);
    }

    public void broadcast(String message) {
        synchronized (clients) {
            for (ClientSession session : clients.values()) {
                session.send(message);
            }
        }
    }

    public boolean isRunning() {
        return running;
    }

    public Map<String, ClientSession> getClientsSnapshot() {
        synchronized (clients) {
            return new HashMap<>(clients);
        }
    }

    public long getUptimeMillis() {
        if (!running) return 0;
        long elapsedNanos = System.nanoTime() - startTime;
        return elapsedNanos / 1_000_000L;
    }
}
