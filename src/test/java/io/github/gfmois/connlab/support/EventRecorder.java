package io.github.gfmois.connlab.support;

import io.github.gfmois.connlab.core.server.ServerEvent;
import io.github.gfmois.connlab.core.server.ServerEventListener;
import io.github.gfmois.connlab.core.server.ServerEventType;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Records the events a server publishes and waits for them (they arrive from the server threads).
 */
public final class EventRecorder implements ServerEventListener {
    private static final long TIMEOUT_MS = 3000;

    private final List<ServerEvent> events = new CopyOnWriteArrayList<>();

    @Override
    public void onServerEvent(ServerEvent event) {
        events.add(event);
    }

    public List<ServerEvent> events() {
        return List.copyOf(events);
    }

    public List<ServerEventType> types() {
        return events.stream().map(ServerEvent::getType).toList();
    }

    public List<ServerEvent> ofType(ServerEventType type) {
        return events.stream().filter(e -> e.getType() == type).toList();
    }

    /**
     * Waits until {@code count} events of {@code type} have been published.
     *
     * @return the events of that type
     */
    public List<ServerEvent> await(ServerEventType type, int count) {
        long deadline = System.currentTimeMillis() + TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            List<ServerEvent> found = ofType(type);
            if (found.size() >= count) {
                return found;
            }
            sleep();
        }
        fail("expected " + count + " " + type + " events, got " + ofType(type).size() + "; all events: " + types());
        return List.of();
    }

    /**
     * @return the first event of {@code type}, waiting for it
     */
    public ServerEvent await(ServerEventType type) {
        return await(type, 1).getFirst();
    }

    private static void sleep() {
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
