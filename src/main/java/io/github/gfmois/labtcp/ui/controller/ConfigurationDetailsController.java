package io.github.gfmois.labtcp.ui.controller;

import io.github.gfmois.labtcp.model.Configuration;
import io.github.gfmois.labtcp.model.response.ResponseType;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
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
    private TextField portField;

    @FXML
    private CheckBox hasToRespondCheckBox;

    @FXML
    private Label noMessagesLabel;

    @FXML
    private TitledPane messagesPane;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        hasToRespondCheckBox.setOnAction(e -> {
            if (currentConfiguration != null) {
                currentConfiguration.setHasToRespond(hasToRespondCheckBox.isSelected());
                if (onFlagsChanged != null) {
                    onFlagsChanged.accept(currentConfiguration);
                }
            }
        });

        portField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (currentConfiguration != null) {
                try {
                    portField.setStyle("-fx-text-fill: black;");
                    int port = Integer.parseInt(newVal);
                    currentConfiguration.setPort(port);
                } catch (NumberFormatException ignored) {
                    portField.setStyle("-fx-text-fill: red;");
                }
            }
        });
    }

    public void setOnFlagsChanged(Consumer<Configuration> onFlagsChanged) {
        this.onFlagsChanged = onFlagsChanged;
    }

    public void showConfiguration(Configuration cfg) {
        this.currentConfiguration = cfg;
        if (cfg == null) {
            configurationSelectedName.setText("");
            configurationSelectedDescription.setText("");
            hasToRespondCheckBox.setSelected(false);
            return;
        }

        configurationSelectedName.setText(cfg.getName());
        configurationSelectedDescription.setText(
                cfg.getDescription() != null ? cfg.getDescription() : "No description set yet"
        );
        hasToRespondCheckBox.setSelected(cfg.isHasToRespond());
        portField.setText(String.valueOf(cfg.getPort()));

        logger.debug("currentConfiguration: {}", currentConfiguration);
        if (currentConfiguration.getMessages() != null && !currentConfiguration.getMessages().isEmpty()) {
            noMessagesLabel.managedProperty().set(false);
            noMessagesLabel.visibleProperty().set(false);

            VBox content = new VBox(10);
            messagesPane.setContent(content);
            currentConfiguration.getMessages().forEach(message -> {
                // Message Title
                Label title = new Label(message.getTitle());
                title.getStyleClass().add("message-title");
                content.getChildren().add(title);

                CheckBox broadcastCheckBox = new CheckBox();
                broadcastCheckBox.setText("Broadcast response");
                broadcastCheckBox.setSelected(message.isBroadcast());
                broadcastCheckBox.setOnAction(e -> {
                    message.setBroadcast(broadcastCheckBox.isSelected());
                });

                content.getChildren().add(broadcastCheckBox);

                var parts = message.getResponseBuilder();
                if (parts == null || parts.isEmpty()) {
                    return;
                }

                if (message.getResponseBuilder() != null && !message.getResponseBuilder().isEmpty()) {
                    message.getResponseBuilder().forEach(part -> {
                        HBox row = new HBox(8);
                        row.setAlignment(Pos.CENTER_LEFT);

                        ChoiceBox<ResponseType> selector = new ChoiceBox<>();
                        selector.getItems().addAll(ResponseType.STATIC, ResponseType.MESSAGE_PART);
                        selector.setConverter(new StringConverter<>() {
                            @Override
                            public String toString(ResponseType t) {
                                if (t == null) return "";
                                return switch (t) {
                                    case STATIC -> "STATIC";
                                    case MESSAGE_PART -> "MESSAGE PART";
                                };
                            }

                            @Override
                            public ResponseType fromString(String s) {
                                throw new UnsupportedOperationException();
                            }
                        });
                        selector.setValue(part.getType() != null ? part.getType() : ResponseType.STATIC);

                        TextField textInput = new TextField();
                        textInput.setPromptText("Static text");
                        textInput.setText(part.getText() != null ? part.getText() : "");
                        HBox.setHgrow(textInput, Priority.ALWAYS);

                        Spinner<Integer> groupIndex = new Spinner<>(0, 99, 0);
                        groupIndex.setEditable(true);
                        groupIndex.setPrefWidth(90);
                        groupIndex.setTooltip(new Tooltip("Regex group index"));

                        if (selector.getValue() == ResponseType.MESSAGE_PART) {
                            int idx = parseIntOrDefault(part.getPartIndex().toString(), 0);
                            groupIndex.getValueFactory().setValue(idx);
                        }

                        StackPane inputPane = new StackPane(textInput, groupIndex);
                        HBox.setHgrow(inputPane, Priority.ALWAYS);

                        applyInputMode(selector.getValue(), textInput, groupIndex);

                        selector.valueProperty().addListener((obs, old, val) -> {
                            if (val == null) val = ResponseType.STATIC;

                            part.setType(val);
                            applyInputMode(val, textInput, groupIndex);

                            if (val == ResponseType.STATIC) {
                                part.setText(textInput.getText());
                            } else {
                                part.setText(String.valueOf(groupIndex.getValue()));
                            }
                        });

                        textInput.textProperty().addListener((obs, old, val) -> {
                            if (selector.getValue() == ResponseType.STATIC) {
                                part.setText(val);
                            }
                        });

                        groupIndex.valueProperty().addListener((obs, old, val) -> {
                            if (selector.getValue() == ResponseType.MESSAGE_PART && val != null) {
                                part.setText(String.valueOf(val));
                            }
                        });

                        row.getChildren().addAll(selector, inputPane);
                        content.getChildren().add(row);
                    });
                }
            });
        } else {
            logger.info("Empty messages or null messages on the current configuration");
            noMessagesLabel.visibleProperty().set(true);
            noMessagesLabel.managedProperty().set(true);
        }
    }

    private static void applyInputMode(ResponseType type, TextField textInput, Spinner<Integer> groupIndex) {
        boolean isMessagePart = type == ResponseType.MESSAGE_PART;

        textInput.setVisible(!isMessagePart);
        textInput.setManaged(!isMessagePart);

        groupIndex.setVisible(isMessagePart);
        groupIndex.setManaged(isMessagePart);
    }

    private static int parseIntOrDefault(String s, int def) {
        if (s == null) return def;
        String t = s.trim();
        if (t.isEmpty()) return def;
        try {
            return Integer.parseInt(t);
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
