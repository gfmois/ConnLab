package io.github.gfmois.connlab.app;

import java.util.StringJoiner;

public class AppSettings {
    private String configurationsPath;
    private Integer lastConfigurationUsedId;
    private Integer maxLogsLines = 1000;

    public AppSettings() {
    }

    public AppSettings(Integer lastConfigurationUsedId, String configurationsPath, Integer maxLogsLines) {
        this.lastConfigurationUsedId = lastConfigurationUsedId;
        this.configurationsPath = configurationsPath;
        this.maxLogsLines = maxLogsLines;
    }

    public Integer getMaxLogsLines() {
        return maxLogsLines;
    }

    public void setMaxLogsLines(Integer maxLogsLines) {
        this.maxLogsLines = maxLogsLines;
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
                .add("maxLogsLines=" + maxLogsLines)
                .toString();
    }
}
