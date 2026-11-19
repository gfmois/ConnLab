package io.github.gfmois.connlab.ui.controller;

import io.github.gfmois.connlab.app.AppSettings;
import io.github.gfmois.connlab.core.server.ClientSession;
import io.github.gfmois.connlab.core.server.ServerEvent;
import io.github.gfmois.connlab.model.ClientRow;
import io.github.gfmois.connlab.model.Configuration;
import io.github.gfmois.connlab.platform.PlatformActions;
import io.github.gfmois.connlab.platform.PlatformFactory;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ConnLabController implements Initializable {
    private static final Logger logger = LogManager.getLogger(ConnLabController.class);
    private final BooleanProperty hasSelectedConfig = new SimpleBooleanProperty(false);
    private final AppSettingsService appSettingsService;
    private final ConfigurationService configurationService;
    private final TcpServerService tcpServerService;
    private List<Configuration> configurations = new ArrayList<>();
    private List<String> configurationNames = new ArrayList<>();
    private final AtomicInteger nextClientId = new AtomicInteger(1);
    private final PlatformActions platform;
    private final Map<String, ObservableList<String>> logsByConfiguration = new HashMap<>();
    private String runningConfigurationName;
    private int maxLogLines = 1000;

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
    private Button openConfigurationsFolder;

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
    private TableColumn<ClientRow, Void> actionColumn;

    @FXML
    private ListView<String> logsListView;

    @FXML
    private TextField messageField;

    public ConnLabController() {
        this.tcpServerService = new TcpServerService();
        this.appSettingsService = new AppSettingsService();
        this.configurationService = new ConfigurationService();
        this.platform = PlatformFactory.create();

        this.uptimeTimeLine = new Timeline(new KeyFrame(Duration.ZERO, e -> updateUptimeLabel()), new KeyFrame(Duration.seconds(1)));
        uptimeTimeLine.setCycleCount(Animation.INDEFINITE);
    }

    private final ChangeListener<String> configurationListSelectionListener = new ChangeListener<>() {
        @Override
        public void changed(ObservableValue<? extends String> observableValue, String oldName, String newName) {
            if (newName == null) {
                // Selection cleared (e.g. while filtering the list): keep showing the current configuration
                return;
            }

            Configuration selectedConfiguration = getConfiguration(newName);
            if (selectedConfiguration == null) {
                logger.warn("Selected configuration '{}' not found", newName);
                return;
            }

            logger.debug("Selected configuration with name: {}", newName);
            hasSelectedConfig.set(true);
            updateServerButtons();
            configurationDetailsController.showConfiguration(selectedConfiguration);
            logsListView.setItems(logsFor(newName));
        }
    };

    private final ChangeListener<String> searchConfigurationListener = (observable, oldValue, newValue) -> applyConfigurationFilter();

    private void applyConfigurationFilter() {
        String query = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
        List<String> visible = query.isEmpty()
                ? configurationNames
                : configurationNames.stream().filter(name -> name.toLowerCase(Locale.ROOT).contains(query)).toList();

        logger.debug("Filter '{}' matched {} configurations", query, visible.size());
        String previouslySelected = configurationsListView.getSelectionModel().getSelectedItem();
        configurationsListView.getItems().setAll(visible);
        if (previouslySelected != null && visible.contains(previouslySelected)) {
            configurationsListView.getSelectionModel().select(previouslySelected);
        }
    }

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
            applyConfigurationFilter();

            if (configurationsListView.getSelectionModel().getSelectedItem() == null) {
                if (configurationsListView.getItems().isEmpty()) {
                    hasSelectedConfig.set(false);
                    configurationDetailsController.showConfiguration(null);
                } else {
                    configurationsListView.getSelectionModel().selectFirst();
                }
            }
        }
    };

    private Configuration getSelectedConfiguration() {
        String name = configurationsListView.getSelectionModel().getSelectedItem();
        if (name == null || name.isEmpty()) return null;
        return getConfiguration(name);
    }

    private Configuration getConfiguration(String configurationName) {
        if (configurationName == null) return null;
        return configurations.stream()
                .filter(c -> configurationName.equals(c.getName()))
                .findFirst()
                .orElse(null);
    }

    private ObservableList<String> logsFor(String configurationName) {
        return logsByConfiguration.computeIfAbsent(configurationName, k -> FXCollections.observableArrayList());
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

        int port = activeConfiguration.getPort();
        if (port < 1 || port > 65535) {
            logger.warn("Invalid port {} for configuration '{}'", port, activeConfiguration.getName());
            new Alert(Alert.AlertType.ERROR, "Invalid port: " + port + ". It must be between 1 and 65535.", ButtonType.OK).showAndWait();
            return;
        }

        try {
            String host = Optional.ofNullable(activeConfiguration.getHost()).orElse("0.0.0.0");
            runningConfigurationName = activeConfiguration.getName();
            tcpServerService.start(host, port, activeConfiguration);
        } catch (IOException e) {
            logger.error("Error while trying to start server", e);
            runningConfigurationName = null;
            onServerError();
            new Alert(Alert.AlertType.ERROR, "Could not start the server on port " + port + ": " + e.getMessage(), ButtonType.OK).showAndWait();
        }
    }

    @FXML
    private void onOpenConfigurationsFolderCLicked() {
        if (this.platform == null) {
            // TODO: Show popup with the error
            logger.error("cannot open explorer because platform is null");
            return;
        }

        try {
            String configurationsPathStr = this.appSettingsService.getAppSettings().getConfigurationsPath();
            if (configurationsPathStr == null || configurationsPathStr.isBlank()) {
                // TODO: Show popup with the error
                logger.error("Error while trying to recover the configurations path, it is {}", configurationsPathStr);
                return;
            }

            Path configurationsPath = Path.of(configurationsPathStr);
            if (!Files.exists(configurationsPath)) {
                // TODO: Show popup with the error
                logger.error("Path does not exists: {}", configurationsPath);
                return;
            }

            this.platform.openFileExplorer(configurationsPath);
        } catch (IOException e) {
            // TODO: Show popup with the error
            logger.error("Error while trying to open the explorer", e);
        }
    }

    @FXML
    private void onStopServerClicked() {
        try {
            // The UI is updated when the STOPPED event arrives, after the pending client events
            tcpServerService.stop();
        } catch (Exception e) {
            onServerError();
            logger.error("Error while trying to stop the server", e);
        }
    }

    private void updateServerButtons() {
        boolean running = tcpServerService.isRunning();
        boolean canStart = !running && hasSelectedConfig.get();
        startServerBtn.setVisible(canStart);
        startServerBtn.setManaged(canStart);
        stopServerBtn.setVisible(running);
        stopServerBtn.setManaged(running);
    }

    private void onServerStarted() {
        updateServerButtons();
        if (runningConfigurationName != null) {
            configurationsListView.getSelectionModel().select(runningConfigurationName);
        }
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
        clientsTable.getItems().clear();
        nextClientId.set(1);
        runningConfigurationName = null;
        updateServerButtons();
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

        startServerBtn.setVisible(false);
        startServerBtn.setManaged(false);

        setupLogs();
        startServerListener();

        clientIdColumn.setCellValueFactory(data -> data.getValue().idProperty().asObject());
        clientAddressColumn.setCellValueFactory(data -> data.getValue().addressProperty());
        actionColumn.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("Kick");

            {
                btn.setOnAction(e -> {
                    ClientRow clientRow = getTableView().getItems().get(getIndex());
                    tcpServerService.kickClient(clientRow.getAddress());
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    setGraphic(btn);
                }
            }
        });

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

        Integer configuredMaxLogLines = appSettingsService.getAppSettings().getMaxLogsLines();
        if (configuredMaxLogLines != null && configuredMaxLogLines > 0) {
            maxLogLines = configuredMaxLogLines;
        }

        configurations = configurationService.getAll();
        configurationNames = configurations.stream().map(Configuration::getName).toList();
        configurationsListView.getItems().addAll(configurationNames);

        if (!configurationsListView.getItems().isEmpty()) {
            configurationsListView.getSelectionModel().selectFirst();
            hasSelectedConfig.set(true);
        }
    }

    private void setupLogs() {
        logsListView.setItems(FXCollections.observableArrayList());
    }

    private void startServerListener() {
        tcpServerService.addListener(event -> {
            Platform.runLater(() -> handleServerEvent(event));
        });
    }

    private void appendLog(String line) {
        String target = runningConfigurationName != null
                ? runningConfigurationName
                : configurationsListView.getSelectionModel().getSelectedItem();
        if (target == null) {
            logger.debug("No configuration to attach log line to: {}", line);
            return;
        }

        ObservableList<String> lines = logsFor(target);
        while (lines.size() >= maxLogLines) {
            lines.removeFirst();
        }
        lines.add(line);

        if (logsListView.getItems() == lines) {
            logsListView.scrollTo(lines.size() - 1);
        }
    }

    private void handleServerEvent(ServerEvent event) {
        DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss:SS");
        String ts = event.getTimestamp().atZone(ZoneId.systemDefault()).format(FMT);

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
        clientsTable.getItems().removeIf(client -> client.getAddress().equals(clientDisconnectedEvent.getRemoteAddress()));
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
