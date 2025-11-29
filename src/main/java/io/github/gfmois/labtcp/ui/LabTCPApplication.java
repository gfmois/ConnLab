package io.github.gfmois.labtcp.ui;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Screen;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.InputStream;

public class LabTCPApplication extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("labtcp-view.fxml"));

        Screen screen = Screen.getPrimary();
        Rectangle2D bounds = screen.getBounds();
        double width = bounds.getWidth() * 0.6;
        double height = bounds.getHeight() * 0.6;

        InputStream iconStream = getClass().getResourceAsStream("/favicon.png");
        if (iconStream == null) {
            throw new IOException("Icon resource not found");
        }
        stage.getIcons().add(new Image(iconStream));

        Scene scene = new Scene(fxmlLoader.load(), width, height);
        stage.setTitle("Lab TCP");
        stage.setScene(scene);
        stage.show();
    }
}
