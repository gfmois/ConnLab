package io.github.gfmois.connlab.ui.components;

import io.github.gfmois.connlab.app.AppSettings;
import io.github.gfmois.connlab.ui.ConnLabApplication;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;

public class PathDialog extends Dialog<String> {
    private static final Logger logger = LogManager.getLogger(PathDialog.class);
    private final TextField pathField = new TextField();

    public PathDialog(AppSettings appSettings) {
        setTitle("Configurations Path");
        setHeaderText("Enter the path of the configurations:");

        // Change Icon
        try {
            Stage stage = (Stage) getDialogPane().getScene().getWindow();
            ConnLabApplication.setAppIcon(stage);
        } catch (NullPointerException e) {
            logger.error("Failed to load dialog icon", e);
        } catch (IOException e) {
            logger.error("Failed to set dialog icon", e);
        }

        // Buttons
        ButtonType okButton = new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        getDialogPane().getButtonTypes().addAll(okButton, cancelButton);

        // Content
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 20, 10 , 20));

        pathField.setPrefWidth(300); // NOTE: Get window width and calc 25%
        pathField.setText(appSettings.getConfigurationsPath());

        grid.add(new Label("Path:"), 0, 0);
        grid.add(pathField, 1, 0);

        getDialogPane().setContent(grid);

        setResultConverter(dialogButton -> {
            if (dialogButton == okButton) {
                return pathField.getText();
            }

            return null;
        });
    }
}
