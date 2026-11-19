package io.github.gfmois.connlab.ui.components;

import io.github.gfmois.connlab.app.AppSettings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/**
 * Asks for the folder where the configuration files are kept.
 */
public class PathDialog extends Dialog<String> {
    private final TextField pathField = new TextField();

    public PathDialog(AppSettings appSettings) {
        setTitle("Configurations folder");
        setHeaderText("Choose the folder where the configurations are kept");
        Dialogs.setIcon(getDialogPane().getScene().getWindow());

        // Buttons
        ButtonType okButton = new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        getDialogPane().getButtonTypes().addAll(okButton, cancelButton);

        // Content
        pathField.setPrefColumnCount(36);
        pathField.setPromptText("C:\\Users\\me\\connlab-configurations");
        if (appSettings != null && appSettings.getConfigurationsPath() != null) {
            pathField.setText(appSettings.getConfigurationsPath());
        }
        HBox.setHgrow(pathField, Priority.ALWAYS);

        Button browse = new Button("Browse…", new FontIcon(Feather.FOLDER));
        browse.setOnAction(e -> browse());

        Label hint = new Label("Every .json file in this folder and its subfolders is a configuration. It is created if it does not exist.");
        hint.setWrapText(true);
        hint.setMaxWidth(480);
        hint.getStyleClass().add("text-muted");

        HBox row = new HBox(8, pathField, browse);
        row.setAlignment(Pos.CENTER_LEFT);
        VBox content = new VBox(10, row, hint);
        content.setPadding(new Insets(20, 20, 10, 20));
        getDialogPane().setContent(content);

        Node ok = getDialogPane().lookupButton(okButton);
        ok.disableProperty().bind(pathField.textProperty().isEmpty());

        setOnShown(e -> pathField.requestFocus());
        setResultConverter(dialogButton -> dialogButton == okButton ? pathField.getText().trim() : null);
    }

    private void browse() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Configurations folder");
        String current = pathField.getText();
        try {
            if (current != null && !current.isBlank() && Files.isDirectory(Path.of(current.trim()))) {
                chooser.setInitialDirectory(new File(current.trim()));
            }
        } catch (InvalidPathException ignored) {
            // Start from the default folder
        }

        File folder = chooser.showDialog(getDialogPane().getScene().getWindow());
        if (folder != null) {
            pathField.setText(folder.getAbsolutePath());
        }
    }
}
