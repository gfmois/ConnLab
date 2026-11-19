package io.github.gfmois.connlab.ui.controller;

import atlantafx.base.controls.CustomTextField;
import atlantafx.base.theme.Styles;
import io.github.gfmois.connlab.app.AppSettings;
import io.github.gfmois.connlab.core.server.ServerEvent;
import io.github.gfmois.connlab.model.ClientRow;
import io.github.gfmois.connlab.model.Configuration;
import io.github.gfmois.connlab.platform.PlatformActions;
import io.github.gfmois.connlab.platform.PlatformFactory;
import io.github.gfmois.connlab.service.AppSettingsService;
import io.github.gfmois.connlab.service.ConfigurationService;
import io.github.gfmois.connlab.service.ConfigurationValidator;
import io.github.gfmois.connlab.service.TcpServerService;
import io.github.gfmois.connlab.ui.LogLine;
import io.github.gfmois.connlab.ui.Theme;
import io.github.gfmois.connlab.ui.components.Dialogs;
import io.github.gfmois.connlab.ui.components.PathDialog;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.Duration;
import javafx.util.StringConverter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.BindException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class ConnLabController implements Initializable {
    private static final Logger logger = LogManager.getLogger(ConnLabController.class);
    /**
     * The "every client" entry of the send target selector.
     */
    private static final ClientRow ALL_CLIENTS = new ClientRow(0, null, null, null);

    private final BooleanProperty hasSelectedConfig = new SimpleBooleanProperty(false);
    private final AppSettingsService appSettingsService;
    private final ConfigurationService configurationService;
    private final TcpServerService tcpServerService;
    private List<Configuration> configurations = new ArrayList<>();
    private List<String> configurationNames = new ArrayList<>();
    private final AtomicInteger nextClientId = new AtomicInteger(1);
    private final PlatformActions platform;
    private final Map<String, ObservableList<LogLine>> logsByConfiguration = new HashMap<>();
    private final ObservableList<ClientRow> sendTargets = FXCollections.observableArrayList(ALL_CLIENTS);
    private String runningConfigurationName;
    /**
     * Set while the list is changed by code, so its selection changes are not taken as the user picking a configuration.
     */
    private boolean updatingList;
    private int maxLogLines = 1000;

    private final Timeline uptimeTimeLine;

    @FXML
    private ListView<String> configurationsListView;

    @FXML
    private VBox noConfigurationsPane;

    @FXML
    private CustomTextField searchField;

    @FXML
    private Label noConfigSelected;

    @FXML
    private Label configurationsPathLabel;

    @FXML
    private Button changeConfigurationsPathButton;

    @FXML
    private Button newConfigurationButton;

    @FXML
    private Button duplicateConfigurationButton;

    @FXML
    private Button deleteConfigurationButton;

    @FXML
    private Button reloadConfigurationsButton;

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
    private TableColumn<ClientRow, String> clientConnectedAtColumn;

    @FXML
    private TableColumn<ClientRow, Void> actionColumn;

    @FXML
    private ListView<LogLine> logsListView;

    @FXML
    private CheckBox followLogsCheckBox;

    @FXML
    private Button copyLogsButton;

    @FXML
    private Button clearLogsButton;

    @FXML
    private ComboBox<ClientRow> sendTargetComboBox;

    @FXML
    private TextField messageField;

    @FXML
    private Button sendButton;

    @FXML
    private Label statusBarLabel;

    @FXML
    private Button themeToggle;

    public ConnLabController() {
        this.tcpServerService = new TcpServerService();
        this.appSettingsService = new AppSettingsService();
        this.configurationService = new ConfigurationService();
        this.platform = PlatformFactory.create();

        this.uptimeTimeLine = new Timeline(new KeyFrame(Duration.ZERO, e -> updateUptimeLabel()), new KeyFrame(Duration.seconds(1)));
        uptimeTimeLine.setCycleCount(Animation.INDEFINITE);
    }

    // ----- Configuration list -----

    private final ChangeListener<String> configurationListSelectionListener = new ChangeListener<>() {
        @Override
        public void changed(ObservableValue<? extends String> observableValue, String oldName, String newName) {
            if (newName == null || updatingList) {
                // Selection cleared (e.g. while filtering the list) or changed by code: keep showing the current configuration
                return;
            }

            if (oldName != null && configurationDetailsController.isDirty()) {
                // Ask about the unsaved changes once this selection change has finished
                Platform.runLater(() -> switchConfiguration(oldName, newName));
                return;
            }

            showConfiguration(newName);
        }
    };

    private void showConfiguration(String name) {
        Configuration selectedConfiguration = getConfiguration(name);
        if (selectedConfiguration == null) {
            logger.warn("Selected configuration '{}' not found", name);
            return;
        }

        logger.debug("Selected configuration with name: {}", name);
        hasSelectedConfig.set(true);
        updateServerButtons();
        configurationDetailsController.showConfiguration(selectedConfiguration);
        logsListView.setItems(logsFor(name));
    }

    /**
     * Goes back to {@code from} while asking what to do with its unsaved changes, and then moves to {@code to}.
     */
    private void switchConfiguration(String from, String to) {
        selectWithoutShowing(from);
        if (confirmUnsavedChanges()) {
            configurationsListView.getSelectionModel().select(to);
        }
    }

    private void selectWithoutShowing(String name) {
        boolean wasUpdating = updatingList;
        updatingList = true;
        try {
            configurationsListView.getSelectionModel().select(name);
        } finally {
            updatingList = wasUpdating;
        }
    }

    /**
     * Asks whether to save or discard the unsaved changes of the configuration being edited.
     *
     * @return true when there are no unsaved changes left and the caller can go on
     */
    public boolean confirmUnsavedChanges() {
        if (!configurationDetailsController.isDirty()) {
            return true;
        }

        Configuration editing = configurationDetailsController.getConfiguration();
        ButtonType save = new ButtonType("Save", ButtonBar.ButtonData.YES);
        ButtonType discard = new ButtonType("Discard", ButtonBar.ButtonData.NO);
        Alert alert = Dialogs.alert(window(), Alert.AlertType.CONFIRMATION, "Save the changes?",
                "\"" + (editing == null ? "" : editing.getName()) + "\" has changes that are not saved.",
                save, discard, ButtonType.CANCEL);

        ButtonType choice = alert.showAndWait().orElse(ButtonType.CANCEL);
        if (choice == save) {
            return configurationDetailsController.save();
        }
        if (choice == discard) {
            configurationDetailsController.discard();
            return true;
        }
        return false;
    }

    private final ChangeListener<String> searchConfigurationListener = (observable, oldValue, newValue) -> applyConfigurationFilter();

    private void applyConfigurationFilter() {
        String query = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
        List<String> visible = query.isEmpty()
                ? configurationNames
                : configurationNames.stream().filter(name -> name.toLowerCase(Locale.ROOT).contains(query)).toList();

        logger.debug("Filter '{}' matched {} configurations", query, visible.size());
        String previouslySelected = configurationsListView.getSelectionModel().getSelectedItem();
        boolean wasUpdating = updatingList;
        updatingList = true;
        try {
            configurationsListView.getItems().setAll(visible);
            if (previouslySelected != null && visible.contains(previouslySelected)) {
                configurationsListView.getSelectionModel().select(previouslySelected);
            }
        } finally {
            updatingList = wasUpdating;
        }
    }

    /**
     * Reads the configurations from disk again and selects {@code name}, or the first one when
     * it is not there.
     *
     * @param show whether to show the selected configuration in the editor
     */
    private void reloadConfigurations(String name, boolean show) {
        configurations = configurationService.getAll();
        configurationNames = configurations.stream().map(Configuration::getName).filter(Objects::nonNull).toList();
        updateConfigurationsPathLabel();

        boolean empty = configurationNames.isEmpty();
        noConfigurationsPane.setVisible(empty);
        noConfigurationsPane.setManaged(empty);
        configurationsListView.setVisible(!empty);

        boolean wasUpdating = updatingList;
        updatingList = true;
        try {
            applyConfigurationFilter();
            configurationsListView.getSelectionModel().clearSelection();
            if (!show && name != null) {
                configurationsListView.getSelectionModel().select(name);
            }
        } finally {
            updatingList = wasUpdating;
        }

        if (!show) {
            return;
        }
        if (name != null && configurationsListView.getItems().contains(name)) {
            configurationsListView.getSelectionModel().select(name);
        } else if (!configurationsListView.getItems().isEmpty()) {
            configurationsListView.getSelectionModel().selectFirst();
        } else {
            hasSelectedConfig.set(false);
            configurationDetailsController.showConfiguration(null);
            updateServerButtons();
        }
    }

    private void updateConfigurationsPathLabel() {
        String path = appSettingsService.getAppSettings().getConfigurationsPath();
        configurationsPathLabel.setText(path == null ? "" : path);
        configurationsPathLabel.setTooltip(path == null ? null : new Tooltip("Configurations folder: " + path));
    }

    /**
     * Saves the draft of the editor; the running server starts answering with it straight away.
     *
     * @return the saved configuration, or null when it could not be saved
     */
    private Configuration saveConfiguration(Configuration draft) {
        Configuration previous = configurations.stream()
                .filter(c -> c.getConfigPath() != null && c.getConfigPath().equals(draft.getConfigPath()))
                .findFirst()
                .orElse(null);

        Configuration saved;
        try {
            saved = configurationService.save(draft);
        } catch (ConfigurationService.InvalidConfigurationException e) {
            Dialogs.error(window(), "The configuration cannot be saved", String.join("\n", e.getErrors()));
            return null;
        } catch (UncheckedIOException e) {
            logger.error("Error while saving configuration '{}'", draft.getName(), e);
            Dialogs.error(window(), "The configuration could not be saved", e.getMessage());
            return null;
        }

        if (previous != null && previous.getName() != null && !previous.getName().equals(saved.getName())) {
            ObservableList<LogLine> logs = logsByConfiguration.remove(previous.getName());
            if (logs != null) logsByConfiguration.put(saved.getName(), logs);
            if (previous.getName().equals(runningConfigurationName)) runningConfigurationName = saved.getName();
        }

        reloadConfigurations(saved.getName(), false);

        Configuration running = tcpServerService.getActiveConfiguration();
        if (running != null && Objects.equals(running.getConfigPath(), saved.getConfigPath())) {
            boolean addressChanged = running.getPort() != saved.getPort()
                    || !hostOrDefault(running.getHost()).equals(hostOrDefault(saved.getHost()));
            tcpServerService.reloadConfiguration(configurationService.copy(saved));
            runningConfigurationName = saved.getName();
            configurationDetailsController.setStatus(addressChanged
                    ? "Saved. The server keeps listening on the old host and port until it is restarted."
                    : "Saved and applied to the running server");
            updateStatusBar();
        }
        return saved;
    }

    private static String hostOrDefault(String host) {
        return host == null || host.isBlank() ? "0.0.0.0" : host.trim();
    }

    @FXML
    private void onNewConfigurationClicked() {
        if (!confirmUnsavedChanges()) return;

        askName("New configuration", "Name of the new configuration:", uniqueName("New configuration"))
                .ifPresent(name -> createAndSelect(() -> configurationService.create(name)));
    }

    @FXML
    private void onDuplicateConfigurationClicked() {
        if (!confirmUnsavedChanges()) return;

        Configuration source = configurationDetailsController.getConfiguration();
        if (source == null) return;

        askName("Duplicate configuration", "Name of the copy of \"" + source.getName() + "\":", uniqueName(source.getName() + " copy"))
                .ifPresent(name -> createAndSelect(() -> configurationService.duplicate(source, name)));
    }

    @FXML
    private void onDeleteConfigurationClicked() {
        Configuration configuration = configurationDetailsController.getConfiguration();
        if (configuration == null) return;

        if (isRunning(configuration)) {
            Dialogs.error(window(), "The configuration is in use", "Stop the server before deleting \"" + configuration.getName() + "\".");
            return;
        }

        if (!Dialogs.confirm(window(), "Delete \"" + configuration.getName() + "\"?",
                "The file " + configuration.getConfigPath() + " will be deleted. This cannot be undone.", "Delete")) {
            return;
        }

        try {
            configurationService.delete(configuration);
        } catch (UncheckedIOException e) {
            logger.error("Error while deleting configuration '{}'", configuration.getName(), e);
            Dialogs.error(window(), "The configuration could not be deleted", e.getMessage());
            return;
        }

        logsByConfiguration.remove(configuration.getName());
        configurationDetailsController.showConfiguration(null);
        reloadConfigurations(null, true);
    }

    @FXML
    private void onReloadConfigurationsClicked() {
        if (!confirmUnsavedChanges()) return;

        reloadConfigurations(configurationsListView.getSelectionModel().getSelectedItem(), true);
    }

    private void createAndSelect(Supplier<Configuration> create) {
        Configuration created;
        try {
            created = create.get();
        } catch (ConfigurationService.InvalidConfigurationException e) {
            Dialogs.error(window(), "The configuration cannot be created", String.join("\n", e.getErrors()));
            return;
        } catch (UncheckedIOException e) {
            logger.error("Error while creating a configuration", e);
            Dialogs.error(window(), "The configuration could not be created", e.getMessage());
            return;
        }

        searchField.clear();
        reloadConfigurations(created.getName(), true);
    }

    private Optional<String> askName(String title, String header, String suggested) {
        TextInputDialog dialog = new TextInputDialog(suggested);
        dialog.setTitle(title);
        dialog.setHeaderText(header);
        dialog.initOwner(window());
        Dialogs.setIcon(dialog.getDialogPane().getScene().getWindow());
        return dialog.showAndWait().map(String::trim).filter(name -> !name.isEmpty());
    }

    private String uniqueName(String base) {
        Set<String> taken = new HashSet<>();
        configurationNames.forEach(name -> taken.add(name.toLowerCase(Locale.ROOT)));
        String name = base;
        for (int i = 2; taken.contains(name.toLowerCase(Locale.ROOT)); i++) {
            name = base + " " + i;
        }
        return name;
    }

    private final EventHandler<ActionEvent> changeConfigurationsPathListener = new EventHandler<>() {
        @Override
        public void handle(ActionEvent actionEvent) {
            if (!confirmUnsavedChanges()) {
                return;
            }

            AppSettings appSettings = appSettingsService.getAppSettings();
            PathDialog setConfigurationsPathDialog = new PathDialog(appSettings);
            setConfigurationsPathDialog.initOwner(window());
            String selectedPath = setConfigurationsPathDialog.showAndWait().orElse(null);
            if (selectedPath == null || selectedPath.isEmpty()) {
                logger.info("User don't want to change configurations path, doing nothing.");
                return;
            }

            try {
                appSettingsService.save(selectedPath, null, true);
            } catch (IllegalArgumentException e) {
                Dialogs.error(window(), "The folder cannot be used", e.getMessage());
                return;
            }
            reloadConfigurations(configurationsListView.getSelectionModel().getSelectedItem(), true);
            logger.info("Found {} configurations on the new path: {}", configurations.size(), selectedPath);
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

    private boolean isRunning(Configuration configuration) {
        Configuration running = tcpServerService.getActiveConfiguration();
        return running != null && configuration != null && Objects.equals(running.getConfigPath(), configuration.getConfigPath());
    }

    private ObservableList<LogLine> logsFor(String configurationName) {
        return logsByConfiguration.computeIfAbsent(configurationName, k -> FXCollections.observableArrayList());
    }

    private Window window() {
        Scene scene = configurationsListView.getScene();
        return scene == null ? null : scene.getWindow();
    }

    // ----- Server -----

    @FXML
    private void onSend() {
        String text = messageField.getText();
        if (text == null || text.isBlank() || !tcpServerService.isRunning()) {
            return;
        }

        ClientRow target = sendTargetComboBox.getValue();
        if (target == null || target == ALL_CLIENTS) {
            tcpServerService.broadcast(text);
        } else {
            tcpServerService.sendToClient(target.getConnectionId(), text);
        }
        messageField.clear();
    }

    @FXML
    private void onStartServerClicked() {
        if (!confirmUnsavedChanges()) {
            return;
        }

        Configuration activeConfiguration = getSelectedConfiguration();
        if (activeConfiguration == null) {
            Dialogs.error(window(), "No configuration selected", "Select the configuration the server has to use.");
            return;
        }

        int port = activeConfiguration.getPort();
        if (port < 1 || port > 65535) {
            logger.warn("Invalid port {} for configuration '{}'", port, activeConfiguration.getName());
            Dialogs.error(window(), "Invalid port", "The port " + port + " is not valid, it must be between 1 and 65535.");
            return;
        }

        String host = hostOrDefault(activeConfiguration.getHost());
        try {
            runningConfigurationName = activeConfiguration.getName();
            tcpServerService.start(host, port, configurationService.copy(activeConfiguration));
        } catch (IOException e) {
            logger.error("Error while trying to start server", e);
            runningConfigurationName = null;
            onServerError();
            String reason = e instanceof BindException
                    ? "The port " + port + " is already in use, or " + host + " is not an address of this computer."
                    : e.getMessage();
            Dialogs.error(window(), "The server could not start on " + host + ":" + port, reason);
        }
    }

    @FXML
    private void onOpenConfigurationsFolderCLicked() {
        String configurationsPathStr = this.appSettingsService.getAppSettings().getConfigurationsPath();
        if (configurationsPathStr == null || configurationsPathStr.isBlank()) {
            Dialogs.error(window(), "No configurations folder", "Choose the configurations folder first.");
            return;
        }

        Path configurationsPath = Path.of(configurationsPathStr);
        if (!Files.isDirectory(configurationsPath)) {
            Dialogs.error(window(), "The folder does not exist", configurationsPath + " does not exist anymore. Choose another folder.");
            return;
        }

        if (this.platform == null) {
            Dialogs.error(window(), "The folder cannot be opened", "Opening folders is not supported on this system. The folder is " + configurationsPath);
            return;
        }

        try {
            this.platform.openFileExplorer(configurationsPath);
        } catch (IOException e) {
            logger.error("Error while trying to open the explorer", e);
            Dialogs.error(window(), "The folder could not be opened", configurationsPath + "\n\n" + e.getMessage());
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
            Dialogs.error(window(), "The server could not be stopped", e.getMessage());
        }
    }

    @FXML
    private void onToggleTheme() {
        Theme.darkProperty().set(!Theme.darkProperty().get());
    }

    @FXML
    private void onClearLogs() {
        logsListView.getItems().clear();
    }

    @FXML
    private void onCopyLogs() {
        List<LogLine> selected = logsListView.getSelectionModel().getSelectedItems();
        List<LogLine> lines = selected.isEmpty() ? logsListView.getItems() : selected;
        ClipboardContent content = new ClipboardContent();
        content.putString(lines.stream().map(LogLine::toString).collect(Collectors.joining(System.lineSeparator())));
        javafx.scene.input.Clipboard.getSystemClipboard().setContent(content);
    }

    private void updateServerButtons() {
        boolean running = tcpServerService.isRunning();
        boolean canStart = !running && hasSelectedConfig.get();
        startServerBtn.setVisible(!running);
        startServerBtn.setManaged(!running);
        startServerBtn.setDisable(!canStart);
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
        setServerStatus("Running", Styles.SUCCESS);
        uptimeTimeLine.playFromStart();
        configurationsListView.refresh();
        updateStatusBar();
    }

    private void onServerStopped() {
        setServerStatus("Stopped", null);
        rightTabPane.getSelectionModel().selectFirst();
        uptimeTimeLine.stop();
        serverUptimeLabel.setText("00:00:00");
        clientsTable.getItems().clear();
        sendTargets.setAll(ALL_CLIENTS);
        sendTargetComboBox.setValue(ALL_CLIENTS);
        nextClientId.set(1);
        runningConfigurationName = null;
        updateServerButtons();
        configurationsListView.refresh();
        updateStatusBar();
    }

    private void onServerError() {
        setServerStatus("Error", Styles.DANGER);
        updateStatusBar();
    }

    private void setServerStatus(String text, String style) {
        serverStatusLabel.setText(text);
        serverStatusLabel.getStyleClass().removeAll(Styles.SUCCESS, Styles.DANGER);
        if (style != null) {
            serverStatusLabel.getStyleClass().add(style);
        }
    }

    private void updateStatusBar() {
        statusBarLabel.getStyleClass().removeAll("status-running");
        if (!tcpServerService.isRunning()) {
            statusBarLabel.setText("Server stopped");
            statusBarLabel.setGraphic(icon(Feather.CIRCLE));
            return;
        }

        Configuration running = tcpServerService.getActiveConfiguration();
        int clients = clientsTable.getItems().size();
        statusBarLabel.getStyleClass().add("status-running");
        statusBarLabel.setGraphic(icon(Feather.RADIO));
        statusBarLabel.setText("Running \"" + runningConfigurationName + "\" on "
                + hostOrDefault(running == null ? null : running.getHost()) + ":" + tcpServerService.getLocalPort()
                + "  ·  " + clients + (clients == 1 ? " client" : " clients"));
    }

    // ----- Setup -----

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        noConfigSelected.visibleProperty().bind(hasSelectedConfig.not());
        noConfigSelected.managedProperty().bind(hasSelectedConfig.not());

        configurationDetails.visibleProperty().bind(hasSelectedConfig);
        configurationDetails.managedProperty().bind(hasSelectedConfig);

        setupIcons();
        setupConfigurationList();
        setupMonitor();
        startServerListener();

        searchField.textProperty().addListener(searchConfigurationListener);
        configurationsListView.getSelectionModel().selectedItemProperty().addListener(configurationListSelectionListener);
        changeConfigurationsPathButton.setOnAction(changeConfigurationsPathListener);

        tcpServerService.addListener(event -> logger.debug("Message Event: {}", event));

        configurationDetailsController.setConfigurationService(configurationService);
        configurationDetailsController.setValidator(cfg -> ConfigurationValidator.validate(cfg, configurations));
        configurationDetailsController.setOnSave(this::saveConfiguration);
        configurationDetailsController.dirtyProperty().addListener((obs, old, dirty) -> configurationsListView.refresh());
        duplicateConfigurationButton.disableProperty().bind(hasSelectedConfig.not());
        deleteConfigurationButton.disableProperty().bind(hasSelectedConfig.not());

        themeToggle.graphicProperty().bind(Bindings.when(Theme.darkProperty())
                .then(icon(Feather.SUN)).otherwise(icon(Feather.MOON)));
        themeToggle.setTooltip(new Tooltip("Switch between the light and dark theme"));

        configurationsListView.sceneProperty().addListener((obs, old, scene) -> {
            if (scene != null) registerShortcuts(scene);
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

        reloadConfigurations(null, true);
        updateServerButtons();
        updateStatusBar();
    }

    private void setupIcons() {
        searchField.setLeft(icon(Feather.SEARCH));
        Button clearSearch = new Button(null, icon(Feather.X));
        clearSearch.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT, Styles.SMALL);
        clearSearch.setCursor(javafx.scene.Cursor.HAND);
        clearSearch.setOnAction(e -> searchField.clear());
        clearSearch.visibleProperty().bind(searchField.textProperty().isNotEmpty());
        searchField.setRight(clearSearch);

        newConfigurationButton.setGraphic(icon(Feather.PLUS));
        newConfigurationButton.setTooltip(new Tooltip("New configuration (Ctrl+N)"));
        iconButton(duplicateConfigurationButton, Feather.COPY, "Duplicate the selected configuration");
        iconButton(deleteConfigurationButton, Feather.TRASH_2, "Delete the selected configuration (Delete)");
        deleteConfigurationButton.getStyleClass().add(Styles.DANGER);
        duplicateConfigurationButton.getStyleClass().add(Styles.FLAT);
        deleteConfigurationButton.getStyleClass().add(Styles.FLAT);
        reloadConfigurationsButton.getStyleClass().add(Styles.FLAT);
        iconButton(reloadConfigurationsButton, Feather.REFRESH_CW, "Read the configurations folder again (F5)");

        changeConfigurationsPathButton.setGraphic(icon(Feather.FOLDER));
        openConfigurationsFolder.setGraphic(icon(Feather.EXTERNAL_LINK));
        startServerBtn.setGraphic(icon(Feather.PLAY));
        stopServerBtn.setGraphic(icon(Feather.SQUARE));
        sendButton.setGraphic(icon(Feather.SEND));
        copyLogsButton.setGraphic(icon(Feather.CLIPBOARD));
        clearLogsButton.setGraphic(icon(Feather.TRASH));
        copyLogsButton.setTooltip(new Tooltip("Copy the selected lines, or every line when none is selected"));
        themeToggle.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT);
    }

    private void setupConfigurationList() {
        configurationsListView.setPlaceholder(new Label("No configuration matches the search"));
        configurationsListView.setCellFactory(list -> new ListCell<>() {
            private final Label name = new Label();
            private final Label running = new Label("running", icon(Feather.RADIO));
            private final Label unsaved = new Label("unsaved");
            private final Region spacer = new Region();
            private final HBox box = new HBox(6, name, spacer, unsaved, running);

            {
                HBox.setHgrow(spacer, Priority.ALWAYS);
                box.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                running.getStyleClass().addAll("list-badge", "status-running");
                unsaved.getStyleClass().addAll("list-badge", Styles.TEXT_MUTED);
                name.setMinWidth(0);
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(null);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }

                name.setText(item);
                boolean isRunning = tcpServerService.isRunning() && item.equals(runningConfigurationName);
                running.setVisible(isRunning);
                running.setManaged(isRunning);
                Configuration editing = configurationDetailsController.getConfiguration();
                boolean isUnsaved = configurationDetailsController.isDirty() && editing != null && item.equals(editing.getName());
                unsaved.setVisible(isUnsaved);
                unsaved.setManaged(isUnsaved);
                setGraphic(box);
            }
        });

        configurationsListView.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.DELETE && hasSelectedConfig.get()) {
                onDeleteConfigurationClicked();
            }
        });
    }

    private void setupMonitor() {
        logsListView.setItems(FXCollections.observableArrayList());
        logsListView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        logsListView.setPlaceholder(new Label("Nothing logged yet"));
        logsListView.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(LogLine line, boolean empty) {
                super.updateItem(line, empty);
                getStyleClass().removeAll("log-info", "log-received", "log-sent", "log-error");
                if (empty || line == null) {
                    setText(null);
                    return;
                }

                setText(line.display());
                getStyleClass().add(switch (line.kind()) {
                    case INFO -> "log-info";
                    case RECEIVED -> "log-received";
                    case SENT -> "log-sent";
                    case ERROR -> "log-error";
                });
            }
        });

        clientIdColumn.setCellValueFactory(data -> data.getValue().idProperty().asObject());
        clientAddressColumn.setCellValueFactory(data -> data.getValue().addressProperty());
        clientConnectedAtColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getConnectedAt()));
        actionColumn.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("Kick", icon(Feather.USER_X));

            {
                btn.getStyleClass().addAll(Styles.SMALL, Styles.DANGER, Styles.FLAT);
                btn.setTooltip(new Tooltip("Close the connection of this client"));
                btn.setOnAction(e -> {
                    ClientRow clientRow = getTableView().getItems().get(getIndex());
                    tcpServerService.kickClient(clientRow.getAddress());
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btn);
            }
        });

        sendTargetComboBox.setItems(sendTargets);
        sendTargetComboBox.setValue(ALL_CLIENTS);
        sendTargetComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(ClientRow row) {
                if (row == null || row == ALL_CLIENTS) return "All clients";
                return "#" + row.getId() + "  " + row.getAddress();
            }

            @Override
            public ClientRow fromString(String s) {
                throw new UnsupportedOperationException();
            }
        });
    }

    private void registerShortcuts(Scene scene) {
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.N, KeyCombination.SHORTCUT_DOWN), this::onNewConfigurationClicked);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.F, KeyCombination.SHORTCUT_DOWN), () -> {
            searchField.requestFocus();
            searchField.selectAll();
        });
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.F5), this::onReloadConfigurationsClicked);
    }

    private static FontIcon icon(Ikon ikon) {
        return new FontIcon(ikon);
    }

    private static void iconButton(Button button, Ikon ikon, String tooltip) {
        button.setGraphic(icon(ikon));
        button.getStyleClass().add(Styles.BUTTON_ICON);
        button.setTooltip(new Tooltip(tooltip));
    }

    // ----- Server events -----

    private void startServerListener() {
        tcpServerService.addListener(event -> Platform.runLater(() -> handleServerEvent(event)));
    }

    private void appendLog(LogLine line) {
        String target = runningConfigurationName != null
                ? runningConfigurationName
                : configurationsListView.getSelectionModel().getSelectedItem();
        if (target == null) {
            logger.debug("No configuration to attach log line to: {}", line);
            return;
        }

        ObservableList<LogLine> lines = logsFor(target);
        while (lines.size() >= maxLogLines) {
            lines.removeFirst();
        }
        lines.add(line);

        if (logsListView.getItems() == lines && followLogsCheckBox.isSelected()) {
            logsListView.scrollTo(lines.size() - 1);
        }
    }

    private void handleServerEvent(ServerEvent event) {
        switch (event.getType()) {
            case STARTED -> {
                appendLog(log(event, LogLine.Kind.INFO, "Server started on " + hostOrDefault(activeHost()) + ":" + tcpServerService.getLocalPort()));
                onServerStarted();
            }
            case STOPPED -> {
                appendLog(log(event, LogLine.Kind.INFO, "Server stopped"));
                onServerStopped();
            }
            case CLIENT_CONNECTED -> {
                appendLog(log(event, LogLine.Kind.INFO, "Client connected"));
                addClientRow(event);
            }
            case CLIENT_DISCONNECTED -> {
                appendLog(log(event, LogLine.Kind.INFO, "Client disconnected"));
                removeClientRow(event);
            }
            case MESSAGE_RECEIVED -> appendLog(log(event, LogLine.Kind.RECEIVED, event.getMessage()));
            case MESSAGE_SENT -> appendLog(log(event, LogLine.Kind.SENT, event.getMessage()));
            case ERROR -> appendLog(log(event, LogLine.Kind.ERROR, String.valueOf(event.getError())));
        }
    }

    private String activeHost() {
        Configuration active = tcpServerService.getActiveConfiguration();
        return active == null ? null : active.getHost();
    }

    private static LogLine log(ServerEvent event, LogLine.Kind kind, String text) {
        return new LogLine(event.getTimestamp(), kind, event.getRemoteAddress(), text);
    }

    private void addClientRow(ServerEvent clientConnectedEvent) {
        int id = nextClientId.getAndIncrement();
        ClientRow clientRow = new ClientRow(id, clientConnectedEvent.getConnectionId(),
                clientConnectedEvent.getRemoteAddress(), clientConnectedEvent.getTimestamp());
        clientsTable.getItems().add(clientRow);
        sendTargets.add(clientRow);
        updateStatusBar();
    }

    private void removeClientRow(ServerEvent clientDisconnectedEvent) {
        clientsTable.getItems().removeIf(client -> client.getAddress().equals(clientDisconnectedEvent.getRemoteAddress()));
        ClientRow selectedTarget = sendTargetComboBox.getValue();
        sendTargets.removeIf(client -> client != ALL_CLIENTS && client.getAddress().equals(clientDisconnectedEvent.getRemoteAddress()));
        if (selectedTarget != null && !sendTargets.contains(selectedTarget)) {
            sendTargetComboBox.setValue(ALL_CLIENTS);
        }
        updateStatusBar();
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
