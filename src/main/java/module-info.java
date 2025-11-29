module io.github.gfmois.labtcp {
    // JavaFX modules
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;

    // Logging
    requires java.logging;
    requires org.apache.logging.log4j;

    // Database
    requires java.sql;
    requires org.xerial.sqlitejdbc;
    requires org.slf4j;
    requires java.desktop;
    requires com.fasterxml.jackson.databind;

    opens io.github.gfmois.labtcp.ui to javafx.fxml;
    opens io.github.gfmois.labtcp.ui.controller to javafx.fxml;

    exports io.github.gfmois.labtcp.ui;
    exports io.github.gfmois.labtcp.ui.controller;
    exports io.github.gfmois.labtcp.app;
    exports io.github.gfmois.labtcp.db;
    exports io.github.gfmois.labtcp.service;
    exports io.github.gfmois.labtcp.model;
    exports io.github.gfmois.labtcp.model.response;
}
