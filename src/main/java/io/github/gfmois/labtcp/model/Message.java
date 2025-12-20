package io.github.gfmois.labtcp.model;

import io.github.gfmois.labtcp.model.response.ResponseMessagePart;

import java.util.List;
import java.util.StringJoiner;

public class Message {
    private String title;
    private boolean broadcast;
    private String receivedRegex;
    private List<ResponseMessagePart> responseBuilder;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getReceivedRegex() {
        return receivedRegex;
    }

    public void setReceivedRegex(String receivedRegex) {
        this.receivedRegex = receivedRegex;
    }

    public List<ResponseMessagePart> getResponseBuilder() {
        return responseBuilder;
    }

    public void setResponseBuilder(List<ResponseMessagePart> responseBuilder) {
        this.responseBuilder = responseBuilder;
    }

    public boolean isBroadcast() {
        return broadcast;
    }

    public void setBroadcast(boolean broadcast) {
        this.broadcast = broadcast;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", Message.class.getSimpleName() + "[", "]")
                .add("title='" + title + "'")
                .add("broadcast=" + broadcast)
                .add("receivedRegex='" + receivedRegex + "'")
                .add("responseBuilder=" + responseBuilder)
                .toString();
    }
}
