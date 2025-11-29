package io.github.gfmois.labtcp.ui.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Label;

public class LabTCPController {
    @FXML
    private Label welcomeText;

    @FXML
    protected void onHelloButtonClick() {
        welcomeText.setText("Welcome to JavaFX Application!");
    }

}
