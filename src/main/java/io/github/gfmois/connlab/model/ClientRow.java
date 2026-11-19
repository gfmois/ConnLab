package io.github.gfmois.connlab.model;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.StringJoiner;

public class ClientRow {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private final IntegerProperty id = new SimpleIntegerProperty();
    private final SimpleStringProperty address = new SimpleStringProperty();
    private final String connectionId;
    private final String connectedAt;

    public ClientRow(Integer id, String address) {
        this(id, null, address, null);
    }

    public ClientRow(Integer id, String connectionId, String address, Instant connectedAt) {
        this.id.set(id);
        this.address.set(address);
        this.connectionId = connectionId;
        this.connectedAt = connectedAt == null ? "" : TIME.format(connectedAt);
    }

    // ID
    public IntegerProperty idProperty() { return id; }
    public int getId() { return id.get(); }
    public void setId(int value) { id.set(value); }

    // Address
    public StringProperty addressProperty() { return address; }
    public String getAddress() { return address.get(); }
    public void setAddress(String value) { address.set(value); }

    /**
     * @return the id the server uses for this connection, to send messages to it
     */
    public String getConnectionId() { return connectionId; }

    /**
     * @return the time of day the client connected
     */
    public String getConnectedAt() { return connectedAt; }

    @Override
    public String toString() {
        return new StringJoiner(", ", ClientRow.class.getSimpleName() + "[", "]")
                .add("id=" + id)
                .add("connectionId=" + connectionId)
                .add("address=" + address)
                .toString();
    }
}
