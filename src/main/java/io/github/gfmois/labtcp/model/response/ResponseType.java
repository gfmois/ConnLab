package io.github.gfmois.labtcp.model.response;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

public enum ResponseType {
    STATIC("static"),
    MESSAGE_PART("messagePart");

    private final String value;

    ResponseType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    private static final Map<String, ResponseType> LOOKUP =
            Arrays.stream(values())
                  .collect(Collectors.toMap(ResponseType::getValue, e -> e));

    @JsonCreator
    public static ResponseType fromValue(String value) {
        ResponseType type = LOOKUP.get(value);
        if (type == null) {
            throw new IllegalArgumentException("Unknown ResponseType: " + value);
        }
        return type;
    }
}
