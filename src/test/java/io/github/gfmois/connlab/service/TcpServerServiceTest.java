package io.github.gfmois.connlab.service;

import io.github.gfmois.connlab.model.Configuration;
import io.github.gfmois.connlab.support.EventRecorder;
import io.github.gfmois.connlab.support.TestClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;

import static io.github.gfmois.connlab.core.server.ServerEventType.*;
import static io.github.gfmois.connlab.support.Configs.*;
import static org.junit.jupiter.api.Assertions.*;

class TcpServerServiceTest {
    private static final Duration SILENCE = Duration.ofMillis(300);

    private TcpServerService service;
    private EventRecorder events;

    @BeforeEach
    void setUp() {
        service = new TcpServerService();
        events = new EventRecorder();
        service.addListener(events);
    }

    @AfterEach
    void tearDown() {
        service.stop();
    }

    private void start(Configuration configuration) throws IOException {
        service.start("127.0.0.1", 0, configuration);
    }

    private TestClient connect() throws IOException {
        int connected = events.ofType(CLIENT_CONNECTED).size();
        TestClient client = TestClient.connect(service.getLocalPort());
        events.await(CLIENT_CONNECTED, connected + 1);
        return client;
    }

    @Test
    void respondsOnlyToTheSender() throws IOException {
        start(configuration(true, helloMessage()));

        try (TestClient sender = connect(); TestClient other = connect()) {
            sender.send("Hello, Juan");

            assertEquals("Bye, Juan!", sender.readLine());
            other.assertNothingReceived(SILENCE);
        }
    }

    @Test
    void broadcastMessagesRespondToEveryClient() throws IOException {
        start(configuration(true, broadcast(helloMessage())));

        try (TestClient sender = connect(); TestClient other = connect()) {
            sender.send("Hello, Ana");

            assertEquals("Bye, Ana!", sender.readLine());
            assertEquals("Bye, Ana!", other.readLine());
        }
    }

    @Test
    void doesNotRespondWhenTheConfigurationIsSetNotTo() throws IOException {
        start(configuration(false, helloMessage()));

        try (TestClient client = connect()) {
            client.send("Hello, Juan");

            events.await(MESSAGE_RECEIVED);
            client.assertNothingReceived(SILENCE);
        }
    }

    @Test
    void unmatchedMessagesAreReportedButNotAnswered() throws IOException {
        start(configuration(true, helloMessage()));

        try (TestClient client = connect()) {
            client.send("something else");

            assertEquals("something else", events.await(MESSAGE_RECEIVED).getMessage());
            client.assertNothingReceived(SILENCE);
        }
    }

    @Test
    void keepsAnsweringAfterSeveralMessages() throws IOException {
        start(configuration(true, helloMessage(), message("ping", "^PING$", text("PONG"))));

        try (TestClient client = connect()) {
            client.send("PING");
            client.send("Hello, Luis");
            client.send("PING");

            assertEquals("PONG", client.readLine());
            assertEquals("Bye, Luis!", client.readLine());
            assertEquals("PONG", client.readLine());
        }
    }

    @Test
    void forwardsTheServerLifecycleEvents() throws IOException {
        start(configuration(true));

        try (TestClient ignored = connect()) {
            events.await(STARTED);
        }
        events.await(CLIENT_DISCONNECTED);
        service.stop();

        assertEquals(STOPPED, events.types().getLast());
    }

    @Test
    void manualBroadcastReachesEveryClient() throws IOException {
        start(configuration(true));

        try (TestClient a = connect(); TestClient b = connect()) {
            service.broadcast("maintenance in 5 minutes");

            assertEquals("maintenance in 5 minutes", a.readLine());
            assertEquals("maintenance in 5 minutes", b.readLine());
            assertEquals(2, events.ofType(MESSAGE_SENT).size());
        }
    }

    @Test
    void kickClientClosesThatClient() throws IOException {
        start(configuration(true));

        try (TestClient kicked = connect(); TestClient stays = connect()) {
            service.kickClient(kicked.address());

            kicked.assertClosedByServer();
            service.broadcast("still here", false);
            assertEquals("still here", stays.readLine());
        }
    }

    @Test
    void stopClosesClientsAndResetsTheState() throws IOException {
        start(configuration(true, helloMessage()));

        try (TestClient client = connect()) {
            service.stop();

            client.assertClosedByServer();
            assertFalse(service.isRunning());
            assertEquals(0, service.getUptimeMillis());
            assertEquals(-1, service.getLocalPort());
        }
    }

    @Test
    void startingAgainRestartsWithTheNewConfiguration() throws IOException {
        start(configuration(true, message("old", "^ping$", text("old"))));
        int firstPort = service.getLocalPort();

        start(configuration(true, message("new", "^ping$", text("new"))));

        assertTrue(service.isRunning());
        assertEquals(1, events.ofType(STOPPED).size());
        try (TestClient client = connect()) {
            client.send("ping");
            assertEquals("new", client.readLine());
        }
        assertTrue(service.getLocalPort() > 0 && firstPort > 0);
    }

    @Test
    void reloadingChangesTheRepliesWithoutDisconnectingClients() throws IOException {
        start(configuration(true, message("ping", "^ping$", text("old"))));

        try (TestClient client = connect()) {
            client.send("ping");
            assertEquals("old", client.readLine());

            Configuration updated = configuration(true, message("ping", "^ping$", text("new")));
            assertTrue(service.reloadConfiguration(updated));
            client.send("ping");

            assertEquals("new", client.readLine());
            assertSame(updated, service.getActiveConfiguration());
            assertEquals(1, events.ofType(STARTED).size());
        }
    }

    @Test
    void reloadingWhileStoppedDoesNothing() {
        assertFalse(service.reloadConfiguration(configuration(true)));
        assertNull(service.getActiveConfiguration());
    }

    @Test
    void operationsWhileStoppedAreNoOps() {
        assertFalse(service.isRunning());
        assertEquals(0, service.getUptimeMillis());
        assertEquals(-1, service.getLocalPort());

        assertDoesNotThrow(() -> {
            service.broadcast("nobody");
            service.sendToClient("nobody", "nothing");
            service.kickClient("/127.0.0.1:1");
            service.stop();
        });
    }
}
