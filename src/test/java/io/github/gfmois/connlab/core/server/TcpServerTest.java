package io.github.gfmois.connlab.core.server;

import io.github.gfmois.connlab.support.EventRecorder;
import io.github.gfmois.connlab.support.TestClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.List;

import static io.github.gfmois.connlab.core.server.ServerEventType.*;
import static org.junit.jupiter.api.Assertions.*;

class TcpServerTest {
    private TcpServer server;
    private EventRecorder events;

    @BeforeEach
    void startServer() throws IOException {
        server = new TcpServer("127.0.0.1", 0);
        events = new EventRecorder();
        server.addListener(events);
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    private TestClient connect() throws IOException {
        int connected = events.ofType(CLIENT_CONNECTED).size();
        TestClient client = TestClient.connect(server.getLocalPort());
        events.await(CLIENT_CONNECTED, connected + 1);
        return client;
    }

    private String connectionIdOf(TestClient client) {
        return events.ofType(CLIENT_CONNECTED).stream()
                .filter(e -> e.getRemoteAddress().equals(client.address()))
                .findFirst().orElseThrow()
                .getConnectionId();
    }

    @Test
    void startPublishesStartedAndListensOnAnEphemeralPort() {
        assertTrue(server.isRunning());
        assertTrue(server.getLocalPort() > 0);
        assertEquals(List.of(STARTED), events.types());
    }

    @Test
    void startingTwiceIsANoOp() throws IOException {
        int port = server.getLocalPort();

        server.start();

        assertEquals(port, server.getLocalPort());
        assertEquals(List.of(STARTED), events.types());
    }

    @Test
    void startFailsWhenThePortIsInUse() throws IOException {
        try (ServerSocket busy = new ServerSocket(0)) {
            TcpServer other = new TcpServer("127.0.0.1", busy.getLocalPort());
            assertThrows(IOException.class, other::start);
            assertFalse(other.isRunning());
        }
    }

    @Test
    void clientConnectionIsPublishedAndTracked() throws IOException {
        try (TestClient client = connect()) {
            ServerEvent connected = events.await(CLIENT_CONNECTED);

            assertEquals(client.address(), connected.getRemoteAddress());
            assertNotNull(connected.getConnectionId());
            assertEquals(1, server.getClientsSnapshot().size());
            assertTrue(server.getClientsSnapshot().containsKey(connected.getConnectionId()));
        }
    }

    @Test
    void receivedLinesArePublishedInOrderAsUtf8() throws IOException {
        try (TestClient client = connect()) {
            client.send("first");
            client.send("Ñandú €");

            List<ServerEvent> received = events.await(MESSAGE_RECEIVED, 2);

            assertEquals("first", received.get(0).getMessage());
            assertEquals("Ñandú €", received.get(1).getMessage());
            assertEquals(client.address(), received.get(0).getRemoteAddress());
        }
    }

    @Test
    void sendToClientRightAfterConnectingReachesOnlyThatClient() throws IOException {
        try (TestClient target = connect(); TestClient other = connect()) {
            // Regression: sending right after CLIENT_CONNECTED used to fail until the session thread
            // had opened its output stream
            server.sendToClient(connectionIdOf(target), "only for you");

            assertEquals("only for you", target.readLine());
            other.assertNothingReceived(Duration.ofMillis(200));
        }
    }

    @Test
    void sendToUnknownClientFails() {
        assertThrows(RetryLimitExceededException.class, () -> server.sendToClient("does-not-exist", "hello"));
    }

    @Test
    void broadcastReachesEveryClientAndCanPublishTheSentMessages() throws IOException {
        try (TestClient a = connect(); TestClient b = connect(); TestClient c = connect()) {
            server.broadcast("to everyone", true);

            assertEquals("to everyone", a.readLine());
            assertEquals("to everyone", b.readLine());
            assertEquals("to everyone", c.readLine());
            assertEquals(3, events.ofType(MESSAGE_SENT).size());
        }
    }

    @Test
    void broadcastWithoutLogDoesNotPublishSentMessages() throws IOException {
        try (TestClient a = connect()) {
            server.broadcast("quiet", false);

            assertEquals("quiet", a.readLine());
            assertTrue(events.ofType(MESSAGE_SENT).isEmpty());
        }
    }

    @Test
    void removeClientClosesOnlyThatClientAtTheFirstAttempt() throws IOException {
        try (TestClient kicked = connect(); TestClient stays = connect()) {
            // Regression: the first kick used to do nothing (compareAndExchange in ClientSession)
            server.removeClient(kicked.address());

            kicked.assertClosedByServer();
            ServerEvent disconnected = events.await(CLIENT_DISCONNECTED);
            assertEquals(kicked.address(), disconnected.getRemoteAddress());
            assertEquals(1, server.getClientsSnapshot().size());

            server.broadcast("still here", false);
            assertEquals("still here", stays.readLine());
        }
    }

    @Test
    void removingAnUnknownOrBlankAddressDoesNothing() throws IOException {
        try (TestClient client = connect()) {
            server.removeClient("/10.0.0.1:1");
            server.removeClient("");
            server.removeClient(null);

            assertEquals(1, server.getClientsSnapshot().size());
            server.broadcast("alive", false);
            assertEquals("alive", client.readLine());
        }
    }

    @Test
    void clientClosingTheConnectionIsPublishedAndUntracked() throws IOException {
        TestClient client = connect();
        String address = client.address();

        client.close();

        assertEquals(address, events.await(CLIENT_DISCONNECTED).getRemoteAddress());
        assertTrue(server.getClientsSnapshot().isEmpty());
    }

    @Test
    void stopClosesEveryClientAndPublishesStoppedLast() throws IOException {
        try (TestClient a = connect(); TestClient b = connect(); TestClient c = connect()) {
            // Regression: stop() left clients connected and could throw ConcurrentModificationException
            server.stop();

            a.assertClosedByServer();
            b.assertClosedByServer();
            c.assertClosedByServer();

            assertFalse(server.isRunning());
            assertEquals(0, server.getUptimeMillis());
            assertTrue(server.getClientsSnapshot().isEmpty());
            assertEquals(3, events.ofType(CLIENT_DISCONNECTED).size());
            assertEquals(STOPPED, events.types().getLast());
        }
    }

    @Test
    void stoppingTwiceIsANoOp() {
        server.stop();
        server.stop();

        assertEquals(1, events.ofType(STOPPED).size());
    }

    @Test
    void uptimeGrowsWhileRunning() throws InterruptedException {
        Thread.sleep(50);

        assertTrue(server.getUptimeMillis() >= 40, "uptime: " + server.getUptimeMillis());
    }

    @Test
    void aFailingListenerDoesNotBreakTheOthers() throws IOException {
        EventRecorder after = new EventRecorder();
        server.addListener(event -> {
            throw new IllegalStateException("listener failure");
        });
        server.addListener(after);

        try (TestClient ignored = connect()) {
            after.await(CLIENT_CONNECTED);
        }
    }

    @Test
    void listensOnTheConfiguredHost() {
        assertEquals("127.0.0.1", server.getLocalAddress());
    }

    @Test
    void listensOnEveryInterfaceWithoutHost() throws IOException {
        for (String host : new String[]{null, "", "0.0.0.0"}) {
            TcpServer everywhere = new TcpServer(host, 0);
            try {
                everywhere.start();
                assertEquals("0.0.0.0", everywhere.getLocalAddress(), "host " + host);
            } finally {
                everywhere.stop();
            }
        }
    }

    @Test
    void anUnknownHostFailsToStart() {
        TcpServer unknown = new TcpServer("no-such-host.invalid", 0);

        assertThrows(IOException.class, unknown::start);
        assertFalse(unknown.isRunning());
    }

    @Test
    void knowsTheAddressOfEachConnection() throws IOException {
        try (TestClient client = connect()) {
            assertEquals(client.address(), server.getRemoteAddress(connectionIdOf(client)));
            assertNull(server.getRemoteAddress("missing"));
        }
    }
}
