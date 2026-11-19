package io.github.gfmois.connlab.support;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * A line-based TCP client to talk to the server under test.
 */
public final class TestClient implements AutoCloseable {
    private static final int DEFAULT_TIMEOUT_MS = 3000;

    private final Socket socket;
    private final BufferedReader in;
    private final OutputStream out;

    private TestClient(Socket socket) throws IOException {
        this.socket = socket;
        this.socket.setSoTimeout(DEFAULT_TIMEOUT_MS);
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.out = socket.getOutputStream();
    }

    public static TestClient connect(int port) throws IOException {
        return new TestClient(new Socket("127.0.0.1", port));
    }

    public void send(String line) throws IOException {
        out.write((line + "\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    /**
     * @return the next line, or null when the server closed the connection
     */
    public String readLine() throws IOException {
        return in.readLine();
    }

    /**
     * Asserts that the server closes the connection (end of stream) within the default timeout.
     */
    public void assertClosedByServer() throws IOException {
        try {
            assertNull(in.readLine(), "expected the server to close the connection");
        } catch (SocketTimeoutException e) {
            fail("the server did not close the connection");
        } catch (IOException e) {
            // A reset is also a closed connection
        }
    }

    /**
     * Asserts that nothing arrives during {@code wait}. Call it last on a client: a timed out read
     * leaves the reader usable, but the wait is lost time for the test.
     */
    public void assertNothingReceived(Duration wait) throws IOException {
        socket.setSoTimeout((int) wait.toMillis());
        try {
            String line = in.readLine();
            fail("expected nothing but received: " + line);
        } catch (SocketTimeoutException expected) {
            // nothing arrived
        } finally {
            socket.setSoTimeout(DEFAULT_TIMEOUT_MS);
        }
    }

    /**
     * @return the address of this client as the server sees it (e.g. {@code /127.0.0.1:54321})
     */
    public String address() {
        return String.valueOf(socket.getLocalSocketAddress());
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
