package io.github.gfmois.connlab.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.StringJoiner;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"type", "text", "partIndex"})
public class ResponseMessagePart {
    private ResponseType type;
    private String text;
    private Integer partIndex;

    public ResponseType getType() {
        return type;
    }

    public void setType(ResponseType type) {
        this.type = type;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public Integer getPartIndex() {
        return partIndex;
    }

    public void setPartIndex(Integer partIndex) {
        this.partIndex = partIndex;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", ResponseMessagePart.class.getSimpleName() + "[", "]")
                .add("type=" + type)
                .add("text='" + text + "'")
                .add("partIndex=" + partIndex)
                .toString();
    }
}
