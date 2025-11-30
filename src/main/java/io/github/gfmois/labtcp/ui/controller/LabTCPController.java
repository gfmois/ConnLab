package io.github.gfmois.labtcp.ui.controller;

import io.github.gfmois.labtcp.app.AppSettings;
import io.github.gfmois.labtcp.model.Configuration;
import io.github.gfmois.labtcp.service.AppSettingsService;
import io.github.gfmois.labtcp.service.ConfigurationService;
import io.github.gfmois.labtcp.service.TcpServerService;
import io.github.gfmois.labtcp.ui.components.PathDialog;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.function.Consumer;

public class LabTCPController implements Initializable {
    private static final Logger logger = LogManager.getLogger(LabTCPController.class);
    private final AppSettingsService appSettingsService;
    private final ConfigurationService configurationService;
    private final TcpServerService tcpServerService;
    private List<Configuration> configurations = new ArrayList<>();
    private List<String> configurationNames = new ArrayList<>();

    @FXML
    private ListView<String> configurationsListView;

    @FXML
    private TextField searchField;

    @FXML
    private Label noConfigSelected;

    @FXML
    private VBox configurationContainer;

    @FXML
    private Label configurationSelectedName;

    @FXML
    private Label configurationSelectedDescription;

    @FXML
    private Button changeConfigurationsPathButton;

    @FXML
    private CheckBox broadcastCheckBox;

    @FXML
    private CheckBox hasToRespondCheckBox;

    @FXML
    private HBox startListeningWrapper;

    @FXML
    private Button startServerBtn;

    @FXML
    private Button stopServerBtn;

    public LabTCPController() {
        this.tcpServerService = new TcpServerService();
        this.appSettingsService = new AppSettingsService();
        this.configurationService = new ConfigurationService();
    }

    private final ChangeListener<String> configurationListSelectionListener = new ChangeListener<>() {
        @Override
        public void changed(ObservableValue<? extends String> observableValue, String s, String t1) {
            String selectedConfigurationName = configurationsListView.getSelectionModel().getSelectedItem();
            noConfigSelected.visibleProperty().set(false); // Hide no configuration selected label
            noConfigSelected.managedProperty().set(false);

            Configuration selectedConfiguration = getConfiguration(selectedConfigurationName);

            hasToRespondCheckBox.setSelected(selectedConfiguration.isHasToRespond());
            broadcastCheckBox.setSelected(selectedConfiguration.isBroadcast());
            configurationSelectedName.textProperty().set(selectedConfiguration.getName());
            configurationSelectedDescription.textProperty().set(selectedConfiguration.getDescription());
            configurationContainer.visibleProperty().set(true); // Set configuration selected container visible
            configurationContainer.managedProperty().set(true);
            startListeningWrapper.visibleProperty().set(true);
            startListeningWrapper.managedProperty().set(true);
        }
    };

    private final ChangeListener<String> searchConfigurationListener = new ChangeListener<>() {
        @Override
        public void changed(ObservableValue<? extends String> observableValue, String s, String t1) {
            String value = observableValue.getValue();
            if (value == null || value.isEmpty()) {
                configurationsListView.getItems().clear();
                configurationsListView.getItems().addAll(configurationNames);
                logger.debug("No value in search input, returning all configurations");
                return;
            }

            List<String> foundConfigurations = configurationNames.stream()
                    .filter(conf -> conf.startsWith(value))
                    .toList();

            logger.debug("Found {} configurations.", foundConfigurations);
            configurationsListView.getItems().clear();
            configurationsListView.getItems().addAll(foundConfigurations);
        }
    };

    private final EventHandler<ActionEvent> changeConfigurationsPathListener = new EventHandler<>() {
        @Override
        public void handle(ActionEvent actionEvent) {
            AppSettings appSettings = appSettingsService.getAppSettings();
            PathDialog setConfigurationsPathDialog = new PathDialog(appSettings);
            String selectedPath = setConfigurationsPathDialog.showAndWait().orElse(null);
            if (selectedPath == null || selectedPath.isEmpty()) {
                logger.info("User don't want to change configurations path, doing nothing.");
                return;
            }

            appSettingsService.save(selectedPath, null, true);
            configurations.clear();
            List<Configuration> configurationsFound = configurationService.getAll();
            logger.info("Found {} configurations on the new path: {}", configurationsFound.size(), selectedPath);
            configurations.addAll(configurationsFound);

            configurationNames = configurations.stream().map(Configuration::getName).toList();

            configurationsListView.getItems().clear();
            configurationsListView.getItems().addAll(configurationNames);
        }
    };

    private Configuration getSelectedConfiguration() {
        String name = configurationsListView.getSelectionModel().getSelectedItem();
        if (name == null || name.isEmpty()) return null;
        return getConfiguration(name);
    }


    private void bindCheckBox(CheckBox box, Consumer<Configuration> updater) {
        box.setOnAction(event -> {
            Configuration cfg = getSelectedConfiguration();
            if (cfg == null) {
                logger.info("No configuration selected");
                return;
            }

            updater.accept(cfg);

            logger.info("New Instance Configuration: {}", cfg);
        });
    }

    private Configuration getConfiguration(String selectedConfigurationName) {
        Configuration selectedConfiguration = configurations.stream()
                .filter(c -> c.getName().equals(selectedConfigurationName))
                .findFirst()
                .orElse(null);
        if (selectedConfiguration == null) {
            throw new IllegalArgumentException("Configuration name is null");
        }
        return selectedConfiguration;
    }

    @FXML
    private void onStartServerClicked() {
        Configuration cfg = getSelectedConfiguration();
        try {
            Configuration activeConfiguration = getSelectedConfiguration();
            if (activeConfiguration == null || activeConfiguration.getPort() == 0) {
                logger.warn("Cannot retrieve selected configuration, using default port");
                activeConfiguration = new Configuration();
                activeConfiguration.setPort(8080);
            }
            tcpServerService.start("0.0.0.0", activeConfiguration.getPort(), cfg);
            startServerBtn.setVisible(false);
            startServerBtn.setManaged(false);
            stopServerBtn.setVisible(true);
            stopServerBtn.setManaged(true);
        } catch (IOException e) {
            logger.error("Error while trying to start server", e);
        }
    }

    @FXML
    private void onStopServerClicked() {
        tcpServerService.stop();
        startServerBtn.setVisible(true);
        startServerBtn.setManaged(true);
        stopServerBtn.setVisible(false);
        stopServerBtn.setManaged(false);
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        searchField.textProperty().addListener(searchConfigurationListener);
        configurationsListView.getSelectionModel().selectedItemProperty().addListener(configurationListSelectionListener);
        changeConfigurationsPathButton.setOnAction(changeConfigurationsPathListener);
        bindCheckBox(hasToRespondCheckBox, cfg -> cfg.setHasToRespond(hasToRespondCheckBox.isSelected()));
        bindCheckBox(broadcastCheckBox, cfg -> cfg.setBroadcast(broadcastCheckBox.isSelected()));

        tcpServerService.addListener(event -> {
            logger.info("Received Message Event from server: {}", event);
        });


        // FIXME: This is OK? I mean, I have the same code in LabTCPApplication
        AppSettings appSettings = appSettingsService.getAppSettings();
        if (appSettings.getConfigurationsPath() == null || appSettings.getConfigurationsPath().isEmpty()) {
            PathDialog setConfigurationsPathDialog = new PathDialog(appSettings);
            String selectedPath = setConfigurationsPathDialog.showAndWait().orElse(null);
            if (selectedPath == null || selectedPath.isEmpty()) {
                logger.info("User don't want to set configurations path, exiting application.");
                System.exit(0);
            }

            appSettingsService.save(selectedPath, null, true);
        }

        configurations = configurationService.getAll();
        configurationNames = configurations.stream().map(Configuration::getName).toList();
        configurationsListView.getItems().addAll(configurationNames);
    }
}
