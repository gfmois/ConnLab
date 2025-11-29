module io.github.gfmois.labtcp {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;
    requires java.logging;
    requires org.apache.logging.log4j;

    opens io.github.gfmois.labtcp.ui to javafx.fxml;
    opens io.github.gfmois.labtcp.ui.controller to javafx.fxml;

    exports io.github.gfmois.labtcp.ui;
    exports io.github.gfmois.labtcp.ui.controller;
}
