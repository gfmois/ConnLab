package io.github.gfmois.connlab.ui.controller;

import io.github.gfmois.connlab.app.AppSettings;
import io.github.gfmois.connlab.core.server.ServerEvent;
import io.github.gfmois.connlab.model.ClientRow;
import io.github.gfmois.connlab.model.Configuration;
import io.github.gfmois.connlab.service.AppSettingsService;
import io.github.gfmois.connlab.service.ConfigurationService;
import io.github.gfmois.connlab.service.TcpServerService;
import io.github.gfmois.connlab.ui.components.PathDialog;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.URL;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicInteger;

public class ConnLabController implements Initializable {
    private static final Logger logger = LogManager.getLogger(ConnLabController.class);
    private final BooleanProperty hasSelectedConfig = new SimpleBooleanProperty(false);
    private final ObservableList<String> logs = FXCollections.observableArrayList();
    private final AppSettingsService appSettingsService;
    private final ConfigurationService configurationService;
    private final TcpServerService tcpServerService;
    private List<Configuration> configurations = new ArrayList<>();
    private List<String> configurationNames = new ArrayList<>();
    private final AtomicInteger nextClientId = new AtomicInteger(1);

    private final Timeline uptimeTimeLine;

    @FXML
    private ListView<String> configurationsListView;

    @FXML
    private TextField searchField;

    @FXML
    private Label noConfigSelected;

    @FXML
    private Button changeConfigurationsPathButton;

    @FXML
    private HBox startListeningWrapper;

    @FXML
    private Button startServerBtn;

    @FXML
    private Button stopServerBtn;

    @FXML
    private ConfigurationDetailsController configurationDetailsController;

    @FXML
    private VBox configurationDetails;

    @FXML
    private TabPane rightTabPane;

    @FXML
    private Tab monitorTab;

    @FXML
    private Label serverStatusLabel;

    @FXML
    private Label serverUptimeLabel;

    @FXML
    private TableView<ClientRow> clientsTable;

    @FXML
    private TableColumn<ClientRow, Integer> clientIdColumn;

    @FXML
    private TableColumn<ClientRow, String> clientAddressColumn;

    @FXML
    private ListView<String> logsListView;

    @FXML
    private TextField messageField;

    public ConnLabController() {
        this.tcpServerService = new TcpServerService();
        this.appSettingsService = new AppSettingsService();
        this.configurationService = new ConfigurationService();

        this.uptimeTimeLine = new Timeline(
                new KeyFrame(Duration.ZERO, e -> updateUptimeLabel()),
                new KeyFrame(Duration.seconds(1))
        );
        uptimeTimeLine.setCycleCount(Animation.INDEFINITE);
    }

    private final ChangeListener<String> configurationListSelectionListener = new ChangeListener<>() {
        @Override
        public void changed(ObservableValue<? extends String> observableValue, String s, String t1) {
            String selectedConfigurationName = configurationsListView.getSelectionModel().getSelectedItem();
            Configuration selectedConfiguration = getConfiguration(selectedConfigurationName);

            logger.debug("Selected configuration with name: {}", selectedConfigurationName);
            hasSelectedConfig.set(true);
            configurationDetailsController.showConfiguration(selectedConfiguration);
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
    private void onSend() {
        String text = messageField.getText();
        if (text == null || text.isBlank()) {
            return;
        }

        tcpServerService.broadcast(text);
        messageField.clear();
    }

    @FXML
    private void onStartServerClicked() {
        Configuration activeConfiguration = getSelectedConfiguration();
        if (activeConfiguration == null) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "Error while trying to recover the current configuration", ButtonType.OK);
            alert.showAndWait();
            return;
        }

        try {
            if (activeConfiguration.getPort() == 0) {
                logger.warn("Cannot retrieve selected configuration, using default port");
                activeConfiguration = new Configuration();
                activeConfiguration.setPort(8080);
            }

            String host = Optional.ofNullable(activeConfiguration.getHost()).orElse("0.0.0.0");
            tcpServerService.start(host, activeConfiguration.getPort(), activeConfiguration);
            onServerStarted();
            startServerBtn.setVisible(false);
            startServerBtn.setManaged(false);
            stopServerBtn.setVisible(true);
            stopServerBtn.setManaged(true);
        } catch (IOException e) {
            logger.error("Error while trying to start server", e);
            onServerError();
        }
    }

    @FXML
    private void onStopServerClicked() {
        try {
            tcpServerService.stop();
            startServerBtn.setVisible(true);
            startServerBtn.setManaged(true);
            stopServerBtn.setVisible(false);
            stopServerBtn.setManaged(false);
            onServerStopped();
        } catch (Exception e) {
            onServerError();
            logger.error("Error while trying to stop the server", e);
        }
    }

    private void onServerStarted() {
        monitorTab.setDisable(false);
        rightTabPane.getSelectionModel().select(monitorTab);
        serverStatusLabel.setText("RUNNING");
        serverStatusLabel.setStyle("-fx-text-fill: forestgreen");
        uptimeTimeLine.playFromStart();
    }

    private void onServerStopped() {
        serverStatusLabel.setStyle("-fx-text-fill: black");
        serverStatusLabel.setText("STOPPED");
        rightTabPane.getSelectionModel().selectFirst();
        uptimeTimeLine.stop();
        serverUptimeLabel.setText("00:00:00");
        // TODO: Clear clients and restart uptime
    }

    private void onServerError() {
        serverStatusLabel.setStyle("-fx-text-fill: red");
        serverStatusLabel.setText("ERROR");
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        noConfigSelected.visibleProperty().bind(hasSelectedConfig.not());
        noConfigSelected.managedProperty().bind(hasSelectedConfig.not());

        configurationDetails.visibleProperty().bind(hasSelectedConfig);
        configurationDetails.managedProperty().bind(hasSelectedConfig);

        startListeningWrapper.visibleProperty().bind(hasSelectedConfig);
        startListeningWrapper.managedProperty().bind(hasSelectedConfig);

        setupLogs();
        startServerListener();

        clientIdColumn.setCellValueFactory(data -> data.getValue().idProperty().asObject());
        clientAddressColumn.setCellValueFactory(data -> data.getValue().addressProperty());

        searchField.textProperty().addListener(searchConfigurationListener);
        configurationsListView.getSelectionModel().selectedItemProperty().addListener(configurationListSelectionListener);
        changeConfigurationsPathButton.setOnAction(changeConfigurationsPathListener);

        tcpServerService.addListener(event -> {
            logger.debug("Message Event: {}", event);
        });

        configurationDetailsController.setOnFlagsChanged(cfg -> {
            logger.info("New configuration selected");
            logger.debug("New Instance Configuration: {}", cfg);
        });

        AppSettings appSettings = appSettingsService.getAppSettings();
        if (appSettings.getConfigurationsPath() == null || appSettings.getConfigurationsPath().isEmpty()) {
            PathDialog setConfigurationsPathDialog = new PathDialog(appSettings);
            String selectedPath = setConfigurationsPathDialog.showAndWait().orElse(null);
            if (selectedPath == null || selectedPath.isEmpty()) {
                logger.info("User don't wanna set configurations path, exiting application.");
                System.exit(0);
            }

            appSettingsService.save(selectedPath, null, true);
        }

        configurations = configurationService.getAll();
        configurationNames = configurations.stream().map(Configuration::getName).toList();
        configurationsListView.getItems().addAll(configurationNames);
    }

    private void setupLogs() {
        logsListView.setItems(logs);
    }

    private void startServerListener() {
        tcpServerService.addListener(event -> {
            Platform.runLater(() -> handleServerEvent(event));
        });
    }

    private void appendLog(String line) {
        AppSettings appSettings = appSettingsService.getAppSettings();
        if (logs.size() >= appSettings.getMaxLogsLines()) {
            logs.removeFirst();
        }

        logs.add(line);
        logsListView.scrollTo(logs.size() - 1);
    }

    private void handleServerEvent(ServerEvent event) {
        DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss:SS");
        String ts = event.getTimestamp()
                .atZone(ZoneId.systemDefault())
                .format(FMT);

        switch (event.getType()) {
            case STARTED -> {
                appendLog(ts + " [INFO]: Server started");
                onServerStarted();
            }
            case STOPPED -> {
                appendLog(ts + " [INFO]: Server stopped");
                onServerStopped();
            }
            case CLIENT_CONNECTED -> {
                appendLog(ts + " [INFO]: Client connected " + event.getRemoteAddress());
                addClientRow(event);
            }
            case CLIENT_DISCONNECTED -> {
                appendLog(ts + " [INFO]: Client disconnected: " + event.getRemoteAddress());
                removeClientRow(event);
            }
            case MESSAGE_RECEIVED -> appendLog(ts + " [RX] [" + event.getRemoteAddress() + "] " + event.getMessage());
            case MESSAGE_SENT -> appendLog(ts + " [TX] [" + event.getRemoteAddress() + "] " + event.getMessage());
            case ERROR -> appendLog(ts + " [ERROR]: " + event.getError());
        }
    }

    private void addClientRow(ServerEvent clientConnectedEvent) {
        int id = nextClientId.getAndIncrement();
        ClientRow clientRow = new ClientRow(id, clientConnectedEvent.getRemoteAddress());
        clientsTable.getItems().add(clientRow);
    }

    private void removeClientRow(ServerEvent clientDisconnectedEvent) {
        clientsTable.getItems().removeIf(
                client -> client.getAddress().equals(clientDisconnectedEvent.getRemoteAddress())
        );
    }

    private void updateUptimeLabel() {
        long uptimeMillis = tcpServerService.getUptimeMillis();
        serverUptimeLabel.setText(formatUptime(uptimeMillis));
    }

    private String formatUptime(long millis) {
        long totalSeconds = millis / 1000;
        long seconds = totalSeconds % 60;
        long minutes = (totalSeconds / 60) % 60;
        long hours = (totalSeconds / 3600) % 24;
        long days = totalSeconds / 86400;

        if (days > 0) {
            return String.format("%dd %02d:%02d:%02d", days, hours, minutes, seconds);
        } else {
            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        }
    }
}
