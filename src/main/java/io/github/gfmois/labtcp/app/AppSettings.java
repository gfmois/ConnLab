package io.github.gfmois.labtcp.app;

import java.util.StringJoiner;

public class AppSettings {
    private String configurationsPath;
    private Integer lastConfigurationUsedId;

    public AppSettings() {
    }

    public AppSettings(Integer lastConfigurationUsedId, String configurationsPath) {
        this.lastConfigurationUsedId = lastConfigurationUsedId;
        this.configurationsPath = configurationsPath;
    }

    public String getConfigurationsPath() {
        return configurationsPath;
    }

    public void setConfigurationsPath(String configurationsPath) {
        this.configurationsPath = configurationsPath;
    }

    public Integer getLastConfigurationUsedId() {
        return lastConfigurationUsedId;
    }

    public void setLastConfigurationUsedId(Integer lastConfigurationUsedId) {
        this.lastConfigurationUsedId = lastConfigurationUsedId;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", AppSettings.class.getSimpleName() + "[", "]")
                .add("configurationsPath='" + configurationsPath + "'")
                .add("lastConfigurationUsedId=" + lastConfigurationUsedId)
                .toString();
    }
}
