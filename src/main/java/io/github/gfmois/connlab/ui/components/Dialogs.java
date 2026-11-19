package io.github.gfmois.connlab.ui.components;

import io.github.gfmois.connlab.ui.ConnLabApplication;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.Optional;

/**
 * Alerts centred on the application window and with its icon.
 */
public final class Dialogs {
    private static final Logger logger = LogManager.getLogger(Dialogs.class);

    private Dialogs() {
    }

    public static Alert alert(Window owner, Alert.AlertType type, String header, String content, ButtonType... buttons) {
        Alert alert = new Alert(type, content, buttons);
        alert.setHeaderText(header);
        alert.setTitle("ConnLab");
        if (owner != null) {
            alert.initOwner(owner);
        }
        setIcon(alert.getDialogPane().getScene().getWindow());
        return alert;
    }

    public static void error(Window owner, String header, String content) {
        alert(owner, Alert.AlertType.ERROR, header, content, ButtonType.OK).showAndWait();
    }

    /**
     * @return whether the user accepted
     */
    public static boolean confirm(Window owner, String header, String content, String acceptText) {
        ButtonType accept = new ButtonType(acceptText, ButtonType.OK.getButtonData());
        Alert alert = alert(owner, Alert.AlertType.CONFIRMATION, header, content, accept, ButtonType.CANCEL);
        Optional<ButtonType> choice = alert.showAndWait();
        return choice.isPresent() && choice.get() == accept;
    }

    public static void setIcon(Window window) {
        if (window instanceof Stage stage) {
            try {
                ConnLabApplication.setAppIcon(stage);
            } catch (IOException e) {
                logger.warn("Could not set the dialog icon", e);
            }
        }
    }
}
