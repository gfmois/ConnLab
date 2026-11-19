package io.github.gfmois.connlab;

import io.github.gfmois.connlab.app.AppPaths;
import io.github.gfmois.connlab.ui.ConnLabApplication;
import javafx.application.Application;

public class Launcher {
    /** Read by log4j2.xml, so it has to be set before the first logger is created. */
    static final String LOGS_DIR_PROPERTY = "connlab.logs.dir";

    public static void main(String[] args) {
        if (System.getProperty(LOGS_DIR_PROPERTY) == null) {
            System.setProperty(LOGS_DIR_PROPERTY, AppPaths.getLogsPath().toString());
        }
        Application.launch(ConnLabApplication.class, args);
    }
}
