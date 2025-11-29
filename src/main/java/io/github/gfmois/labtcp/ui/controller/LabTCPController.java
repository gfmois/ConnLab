package io.github.gfmois.labtcp.ui.controller;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

public class LabTCPController implements Initializable {
    private final List<String> configurations = new ArrayList<>();
    private String selectedConfiguration;

    @FXML
    private ListView<String> configurationsListView;

    @FXML
    private TextField searchField;

    private final ChangeListener<String> configurationListSelectionListener = new ChangeListener<>() {
        @Override
        public void changed(ObservableValue<? extends String> observableValue, String s, String t1) {
            selectedConfiguration = configurationsListView.getSelectionModel().getSelectedItem();
        }
    };

    private final ChangeListener<String> searchConfigurationListener = new ChangeListener<String>() {
        @Override
        public void changed(ObservableValue<? extends String> observableValue, String s, String t1) {
            String value = observableValue.getValue();
            if (value == null || value.isEmpty()) {
                configurationsListView.getItems().clear();
                configurationsListView.getItems().addAll(configurations);
                return;
            }

            List<String> foundConfigurations = configurations.stream()
                    .filter(conf -> conf.startsWith(value))
                    .toList();

            configurationsListView.getItems().clear();
            configurationsListView.getItems().addAll(foundConfigurations);

        }
    };

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        configurations.addAll(List.of(new String[]{"asdf", "1234", "test"}));

        searchField.textProperty().addListener(searchConfigurationListener);

        configurationsListView.getItems().addAll(configurations);
        configurationsListView.getSelectionModel().selectedItemProperty().addListener(configurationListSelectionListener);
    }
}
