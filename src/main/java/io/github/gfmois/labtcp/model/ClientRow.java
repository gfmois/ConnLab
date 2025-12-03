package io.github.gfmois.labtcp.model;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.util.StringJoiner;

public class ClientRow {
    private final IntegerProperty id = new SimpleIntegerProperty();
    private final SimpleStringProperty address = new SimpleStringProperty();

    public ClientRow(Integer id, String address) {
        this.id.set(id);
        this.address.set(address);
    }

    // ID
    public IntegerProperty idProperty() { return id; }
    public int getId() { return id.get(); }
    public void setId(int value) { id.set(value); }

    // Address
    public StringProperty addressProperty() { return address; }
    public String getAddress() { return address.get(); }
    public void setAddress(String value) { address.set(value); }

    @Override
    public String toString() {
        return new StringJoiner(", ", ClientRow.class.getSimpleName() + "[", "]")
                .add("id=" + id)
                .add("address=" + address)
                .toString();
    }
}
