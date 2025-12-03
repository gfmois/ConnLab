package io.github.gfmois.labtcp.ui.controller;

import io.github.gfmois.labtcp.model.Configuration;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.URL;
import java.util.ResourceBundle;
import java.util.function.Consumer;

public class ConfigurationDetailsController implements Initializable {
    private static final Logger logger = LogManager.getLogger(ConfigurationDetailsController.class);
    private Configuration currentConfiguration;
    private Consumer<Configuration> onFlagsChanged;

    @FXML
    private VBox configurationContainer;

    @FXML
    private Label configurationSelectedName;

    @FXML
    private Label configurationSelectedDescription;

    @FXML
    private CheckBox broadcastCheckBox;

    @FXML
    private CheckBox hasToRespondCheckBox;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        broadcastCheckBox.setOnAction(e -> {
            if (currentConfiguration != null) {
                currentConfiguration.setBroadcast(broadcastCheckBox.isSelected());
                if (onFlagsChanged != null) {
                    onFlagsChanged.accept(currentConfiguration);
                }
            }
        });

        hasToRespondCheckBox.setOnAction(e -> {
            if (currentConfiguration != null) {
                currentConfiguration.setHasToRespond(hasToRespondCheckBox.isSelected());
                if (onFlagsChanged != null) {
                    onFlagsChanged.accept(currentConfiguration);
                }
            }
        });
    }

    public void setOnFlagsChanged(Consumer<Configuration> onFlagsChanged) {
        this.onFlagsChanged = onFlagsChanged;
    }

    public void showConfiguration(Configuration cfg) {
        this.configurationContainer.setManaged(true);
        this.configurationContainer.setVisible(true);
        this.currentConfiguration = cfg;
        if (cfg == null) {
            configurationSelectedName.setText("");
            configurationSelectedDescription.setText("");
            broadcastCheckBox.setSelected(false);
            hasToRespondCheckBox.setSelected(false);
            return;
        }

        configurationSelectedName.setText(cfg.getName());
        configurationSelectedDescription.setText(
                cfg.getDescription() != null ? cfg.getDescription() : "No description set yet"
        );
        broadcastCheckBox.setSelected(cfg.isBroadcast());
        hasToRespondCheckBox.setSelected(cfg.isHasToRespond());
    }
}
