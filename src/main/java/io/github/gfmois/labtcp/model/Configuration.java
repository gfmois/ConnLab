package io.github.gfmois.labtcp.model;

import java.util.List;
import java.util.StringJoiner;

public class Configuration {
    private Integer id;
    private String name;
    private String description;
    private String configPath;
    private int port;
    private boolean hasToRespond;
    private List<Message> messages;

    public Configuration() {}

    public Configuration(
            Integer id,
            String name,
            String description,
            String configPath,
            int port,
            boolean hasToRespond,
            List<Message> messages
    ) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.configPath = configPath;
        this.port = port;
        this.hasToRespond = hasToRespond;
        this.messages = messages;
    }

    public Configuration(
            String name,
            String description,
            String configPath,
            int port,
            boolean hasToRespond,
            List<Message> messages
    ) {
        this(null, name, description, configPath, port, hasToRespond, messages);
    }

    public Configuration(
            String name,
            String description,
            int port,
            boolean hasToRespond,
            List<Message> messages
    ) {
        this(null, name, description, null, port, hasToRespond, messages);
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getConfigPath() {
        return configPath;
    }

    public void setConfigPath(String configPath) {
        this.configPath = configPath;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isHasToRespond() {
        return hasToRespond;
    }

    public void setHasToRespond(boolean hasToRespond) {
        this.hasToRespond = hasToRespond;
    }

    public List<Message> getMessages() {
        return messages;
    }

    public void setMessages(List<Message> messages) {
        this.messages = messages;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", Configuration.class.getSimpleName() + "[", "]")
                .add("id=" + id)
                .add("name='" + name + "'")
                .add("description='" + description + "'")
                .add("configPath='" + configPath + "'")
                .add("port=" + port)
                .add("hasToRespond=" + hasToRespond)
                .add("messages=" + messages)
                .toString();
    }
}
