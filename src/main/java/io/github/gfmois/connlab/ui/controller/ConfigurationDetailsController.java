package io.github.gfmois.connlab.ui.controller;

import atlantafx.base.theme.Styles;
import io.github.gfmois.connlab.core.response.ResponseEngine;
import io.github.gfmois.connlab.model.Configuration;
import io.github.gfmois.connlab.model.Message;
import io.github.gfmois.connlab.model.response.ResponseMessagePart;
import io.github.gfmois.connlab.model.response.ResponseType;
import io.github.gfmois.connlab.service.ConfigurationService;
import io.github.gfmois.connlab.service.ConfigurationValidator;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Editor of the selected configuration.
 * <p>
 * Edits are made on a draft copy; the configuration shown in the list and used by the server
 * only changes when the draft is saved.
 */
public class ConfigurationDetailsController implements Initializable {
    private static final KeyCombination SAVE_SHORTCUT = KeyCombination.keyCombination("Shortcut+S");

    private final ReadOnlyBooleanWrapper dirty = new ReadOnlyBooleanWrapper(false);
    private final ResponseEngine responseEngine = new ResponseEngine();

    private ConfigurationService configurationService = new ConfigurationService();
    private Function<Configuration, List<String>> validator = cfg -> ConfigurationValidator.validate(cfg, List.of());
    private Function<Configuration, Configuration> onSave;

    private Configuration original;
    private Configuration draft;
    private String savedJson;
    private boolean loading;
    private List<String> errors = List.of();

    @FXML
    private VBox configurationContainer;

    @FXML
    private Label statusLabel;

    @FXML
    private Button discardButton;

    @FXML
    private Button saveButton;

    @FXML
    private Label errorsLabel;

    @FXML
    private TextField nameField;

    @FXML
    private TextField descriptionField;

    @FXML
    private Label fileLabel;

    @FXML
    private TextField hostField;

    @FXML
    private TextField portField;

    @FXML
    private CheckBox hasToRespondCheckBox;

    @FXML
    private Label noMessagesLabel;

    @FXML
    private VBox messagesBox;

    @FXML
    private TextField tryField;

    @FXML
    private Label tryResultLabel;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        nameField.textProperty().addListener((obs, old, val) -> edit(cfg -> cfg.setName(val)));
        descriptionField.textProperty().addListener((obs, old, val) -> edit(cfg -> cfg.setDescription(val)));
        hostField.textProperty().addListener((obs, old, val) -> edit(cfg -> cfg.setHost(val)));
        portField.textProperty().addListener((obs, old, val) -> edit(cfg -> cfg.setPort(parsePort(val))));
        hasToRespondCheckBox.selectedProperty().addListener((obs, old, val) -> edit(cfg -> cfg.setHasToRespond(val)));
        tryField.textProperty().addListener((obs, old, val) -> updateTryIt());

        // Ctrl+S (Cmd+S on macOS) saves, once the editor is attached to the window
        configurationContainer.sceneProperty().addListener((obs, oldScene, scene) -> {
            if (oldScene != null) oldScene.getAccelerators().remove(SAVE_SHORTCUT);
            if (scene != null) scene.getAccelerators().put(SAVE_SHORTCUT, () -> {
                if (draft != null && !saveButton.isDisabled()) save();
            });
        });
    }

    public void setConfigurationService(ConfigurationService configurationService) {
        this.configurationService = configurationService;
    }

    /**
     * @param validator returns the problems that prevent saving a configuration
     */
    public void setValidator(Function<Configuration, List<String>> validator) {
        this.validator = validator;
    }

    /**
     * @param onSave saves the draft and returns the saved configuration, or null when it could not
     *               be saved; it may call {@link #setStatus(String)} to replace the "Saved" message
     */
    public void setOnSave(Function<Configuration, Configuration> onSave) {
        this.onSave = onSave;
    }

    /**
     * Whether the draft has changes that are not saved.
     */
    public ReadOnlyBooleanProperty dirtyProperty() {
        return dirty.getReadOnlyProperty();
    }

    public boolean isDirty() {
        return dirty.get();
    }

    /**
     * @return the configuration being edited, as last saved
     */
    public Configuration getConfiguration() {
        return original;
    }

    public void setStatus(String status) {
        statusLabel.setText(status == null ? "" : status);
    }

    /**
     * Shows {@code cfg} for editing, discarding the unsaved changes of the previous one.
     */
    public void showConfiguration(Configuration cfg) {
        setStatus("");
        load(cfg);
    }

    private void load(Configuration cfg) {
        original = cfg;
        draft = cfg == null ? null : configurationService.copy(cfg);
        savedJson = cfg == null ? null : configurationService.toJson(draft);

        loading = true;
        try {
            nameField.setText(draft == null ? "" : orEmpty(draft.getName()));
            descriptionField.setText(draft == null ? "" : orEmpty(draft.getDescription()));
            hostField.setText(draft == null ? "" : orEmpty(draft.getHost()));
            portField.setText(draft == null ? "" : String.valueOf(draft.getPort()));
            hasToRespondCheckBox.setSelected(draft != null && draft.isHasToRespond());
            fileLabel.setText(cfg == null || cfg.getConfigPath() == null ? "" : cfg.getConfigPath());
        } finally {
            loading = false;
        }

        renderMessages();
    }

    /**
     * Saves the draft.
     *
     * @return true when it was saved, false when it is not valid or the save failed
     */
    public boolean save() {
        if (draft == null || onSave == null) {
            return false;
        }
        if (!errors.isEmpty()) {
            setStatus("Fix the errors before saving");
            return false;
        }

        Configuration saved = onSave.apply(draft);
        if (saved == null) {
            return false;
        }

        load(saved);
        if (statusLabel.getText().isEmpty()) {
            setStatus("Saved");
        }
        return true;
    }

    /**
     * Throws away the unsaved changes.
     */
    public void discard() {
        showConfiguration(original);
    }

    @FXML
    private void onSave() {
        save();
    }

    @FXML
    private void onDiscard() {
        discard();
    }

    @FXML
    private void onAddMessage() {
        if (draft == null) return;

        Message message = new Message();
        message.setReceivedRegex("");
        message.setResponseBuilder(new ArrayList<>(List.of(staticPart(""))));
        draft.getMessages().add(message);
        renderMessages();
    }

    private void edit(Consumer<Configuration> change) {
        if (loading || draft == null) return;
        change.accept(draft);
        changed();
    }

    /**
     * Refreshes everything that depends on the draft: unsaved state, errors and the "Try it" result.
     */
    private void changed() {
        if (draft == null) {
            dirty.set(false);
            errors = List.of();
        } else {
            dirty.set(!configurationService.toJson(draft).equals(savedJson));
            errors = validator.apply(draft);
        }

        portField.pseudoClassStateChanged(Styles.STATE_DANGER,
                draft != null && (draft.getPort() < 1 || draft.getPort() > 65535));
        nameField.pseudoClassStateChanged(Styles.STATE_DANGER,
                draft != null && errors.stream().anyMatch(e -> e.startsWith("The name") || e.startsWith("There is already")));

        errorsLabel.setText(String.join("\n", errors.stream().map(e -> "• " + e).toList()));
        errorsLabel.setVisible(!errors.isEmpty());
        errorsLabel.setManaged(!errors.isEmpty());

        saveButton.setDisable(!dirty.get() || !errors.isEmpty());
        discardButton.setDisable(!dirty.get());
        if (dirty.get()) {
            setStatus("Unsaved changes");
        } else if ("Unsaved changes".equals(statusLabel.getText())) {
            setStatus("");
        }
        updateTryIt();
    }

    private void updateTryIt() {
        String incoming = tryField.getText();
        if (draft == null || incoming == null || incoming.isEmpty()) {
            tryResultLabel.setText("The reply the server would send appears here.");
            return;
        }

        Optional<ResponseEngine.Reply> reply = responseEngine.respond(draft, incoming);
        if (reply.isEmpty()) {
            tryResultLabel.setText("No message matches: the server does not reply.");
            return;
        }

        Message matched = reply.get().message();
        int number = draft.getMessages().indexOf(matched) + 1;
        String title = matched.getTitle() == null || matched.getTitle().isBlank() ? "" : " (" + matched.getTitle().trim() + ")";
        StringBuilder text = new StringBuilder("Reply: \"").append(reply.get().text()).append('"')
                .append("\nMatched message ").append(number).append(title)
                .append(matched.isBroadcast() ? ", sent to every client" : ", sent to the sender");
        if (!draft.isHasToRespond()) {
            text.append("\nAuto respond is off, so the server will not send it.");
        }
        tryResultLabel.setText(text.toString());
    }

    // ----- Messages -----

    private void renderMessages() {
        messagesBox.getChildren().clear();
        List<Message> messages = draft == null ? List.of() : draft.getMessages();
        for (int i = 0; i < messages.size(); i++) {
            messagesBox.getChildren().add(messageCard(messages, i));
        }

        noMessagesLabel.setVisible(messages.isEmpty());
        noMessagesLabel.setManaged(messages.isEmpty());
        changed();
    }

    private Node messageCard(List<Message> messages, int index) {
        Message message = messages.get(index);

        Label number = new Label("#" + (index + 1));
        number.getStyleClass().add("message-title");

        TextField title = new TextField(orEmpty(message.getTitle()));
        title.setPromptText("Title (optional)");
        HBox.setHgrow(title, Priority.ALWAYS);
        title.textProperty().addListener((obs, old, val) -> {
            message.setTitle(val);
            changed();
        });

        CheckBox broadcast = new CheckBox("Reply to every client");
        broadcast.setSelected(message.isBroadcast());
        broadcast.setTooltip(new Tooltip("Send the reply to all connected clients instead of only the sender"));
        broadcast.selectedProperty().addListener((obs, old, val) -> {
            message.setBroadcast(val);
            changed();
        });

        HBox header = new HBox(8, number, title, broadcast,
                moveButton(Feather.ARROW_UP, "Move up", messages, index, -1),
                moveButton(Feather.ARROW_DOWN, "Move down", messages, index, 1),
                removeButton("Delete this message", () -> messages.remove(index)));
        header.setAlignment(Pos.CENTER_LEFT);

        TextField regex = new TextField(orEmpty(message.getReceivedRegex()));
        regex.setPromptText("Regex, e.g. ^Hello, (.*)$");
        HBox.setHgrow(regex, Priority.ALWAYS);
        Label regexInfo = new Label();
        regexInfo.getStyleClass().add("message-meta");
        updateRegexInfo(regex, regexInfo, message.getReceivedRegex());
        regex.textProperty().addListener((obs, old, val) -> {
            message.setReceivedRegex(val);
            updateRegexInfo(regex, regexInfo, val);
            changed();
        });
        HBox regexRow = new HBox(8, fixedLabel("When a client sends"), regex, regexInfo);
        regexRow.setAlignment(Pos.CENTER_LEFT);

        VBox parts = new VBox(6);
        List<ResponseMessagePart> responseBuilder = message.getResponseBuilder();
        for (int i = 0; i < responseBuilder.size(); i++) {
            parts.getChildren().add(partRow(message, responseBuilder, i));
        }

        Button addText = new Button("Text", new FontIcon(Feather.PLUS));
        addText.getStyleClass().add(Styles.SMALL);
        addText.setTooltip(new Tooltip("Add fixed text to the reply"));
        addText.setOnAction(e -> {
            responseBuilder.add(staticPart(""));
            renderMessages();
        });
        Button addGroup = new Button("Regex group", new FontIcon(Feather.PLUS));
        addGroup.getStyleClass().add(Styles.SMALL);
        addGroup.setTooltip(new Tooltip("Insert the text captured by a group of the regex"));
        addGroup.setOnAction(e -> {
            responseBuilder.add(groupPart(defaultGroup(message)));
            renderMessages();
        });
        HBox addParts = new HBox(8, addText, addGroup);

        VBox card = new VBox(8, header, regexRow, fixedLabel("Reply with"), parts, addParts);
        card.getStyleClass().add("message-card");
        card.setPadding(new Insets(10));
        return card;
    }

    private Node partRow(Message message, List<ResponseMessagePart> parts, int index) {
        ResponseMessagePart part = parts.get(index);

        ChoiceBox<ResponseType> type = new ChoiceBox<>();
        type.getItems().addAll(ResponseType.STATIC, ResponseType.MESSAGE_PART);
        type.setConverter(new StringConverter<>() {
            @Override
            public String toString(ResponseType t) {
                if (t == null) return "";
                return switch (t) {
                    case STATIC -> "Text";
                    case MESSAGE_PART -> "Regex group";
                };
            }

            @Override
            public ResponseType fromString(String s) {
                throw new UnsupportedOperationException();
            }
        });
        type.setValue(part.getType() != null ? part.getType() : ResponseType.STATIC);
        type.setPrefWidth(120);

        TextField text = new TextField(orEmpty(part.getText()));
        text.setPromptText("Text to send");
        text.textProperty().addListener((obs, old, val) -> {
            part.setText(val);
            changed();
        });

        Spinner<Integer> group = new Spinner<>(0, 99, part.getPartIndex() == null ? 0 : Math.max(0, part.getPartIndex()));
        group.setEditable(true);
        group.setPrefWidth(90);
        group.setTooltip(new Tooltip("Number of the regex group; 0 is the whole message"));
        group.valueProperty().addListener((obs, old, val) -> {
            if (val != null) {
                part.setPartIndex(val);
                changed();
            }
        });
        // Apply typed numbers right away instead of only on Enter or when the field loses focus
        group.getEditor().textProperty().addListener((obs, old, val) -> {
            try {
                group.getValueFactory().setValue(Integer.parseInt(val.trim()));
            } catch (NumberFormatException ignored) {
                // Keep the last valid value
            }
        });
        Label groupHint = new Label("group of the regex");
        groupHint.getStyleClass().add("message-meta");
        HBox groupBox = new HBox(8, group, groupHint);
        groupBox.setAlignment(Pos.CENTER_LEFT);

        StackPane input = new StackPane(text, groupBox);
        StackPane.setAlignment(groupBox, Pos.CENTER_LEFT);
        HBox.setHgrow(input, Priority.ALWAYS);
        showInput(type.getValue(), text, groupBox);

        type.valueProperty().addListener((obs, old, val) -> {
            ResponseType selected = val == null ? ResponseType.STATIC : val;
            part.setType(selected);
            if (selected == ResponseType.STATIC) {
                part.setText(text.getText());
            } else {
                if (part.getPartIndex() == null) part.setPartIndex(defaultGroup(message));
                group.getValueFactory().setValue(part.getPartIndex());
            }
            showInput(selected, text, groupBox);
            changed();
        });

        HBox row = new HBox(8, type, input,
                moveButton(Feather.ARROW_UP, "Move up", parts, index, -1),
                moveButton(Feather.ARROW_DOWN, "Move down", parts, index, 1),
                removeButton("Delete this part", () -> parts.remove(index)));
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private <T> Button moveButton(Ikon icon, String tooltip, List<T> items, int index, int delta) {
        Button button = smallButton(icon, tooltip);
        int target = index + delta;
        button.setDisable(target < 0 || target >= items.size());
        button.setOnAction(e -> {
            Collections.swap(items, index, target);
            renderMessages();
        });
        return button;
    }

    private Button removeButton(String tooltip, Runnable remove) {
        Button button = smallButton(Feather.X, tooltip);
        button.getStyleClass().add(Styles.DANGER);
        button.setOnAction(e -> {
            remove.run();
            renderMessages();
        });
        return button;
    }

    private static Button smallButton(Ikon icon, String tooltip) {
        Button button = new Button(null, new FontIcon(icon));
        button.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT, Styles.SMALL);
        button.setTooltip(new Tooltip(tooltip));
        button.setFocusTraversable(false);
        return button;
    }

    private static Label fixedLabel(String text) {
        Label label = new Label(text);
        label.setMinWidth(Region.USE_PREF_SIZE);
        return label;
    }

    private static void updateRegexInfo(TextField field, Label info, String regex) {
        String error = ConfigurationValidator.regexError(regex);
        field.pseudoClassStateChanged(Styles.STATE_DANGER, error != null);
        field.setTooltip(error == null ? null : new Tooltip(error));
        if (error != null) {
            info.setText(regex == null || regex.isEmpty() ? "required" : "invalid");
        } else {
            int groups = ConfigurationValidator.groupCount(regex);
            info.setText(groups == 1 ? "1 group" : groups + " groups");
        }
    }

    private static void showInput(ResponseType type, TextField text, Node group) {
        boolean isGroup = type == ResponseType.MESSAGE_PART;
        text.setVisible(!isGroup);
        text.setManaged(!isGroup);
        group.setVisible(isGroup);
        group.setManaged(isGroup);
    }

    private static int defaultGroup(Message message) {
        return ConfigurationValidator.groupCount(message.getReceivedRegex()) >= 1 ? 1 : 0;
    }

    private static ResponseMessagePart staticPart(String text) {
        ResponseMessagePart part = new ResponseMessagePart();
        part.setType(ResponseType.STATIC);
        part.setText(text);
        return part;
    }

    private static ResponseMessagePart groupPart(int index) {
        ResponseMessagePart part = new ResponseMessagePart();
        part.setType(ResponseType.MESSAGE_PART);
        part.setPartIndex(index);
        return part;
    }

    private static int parsePort(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException | NullPointerException e) {
            return -1;
        }
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }
}
