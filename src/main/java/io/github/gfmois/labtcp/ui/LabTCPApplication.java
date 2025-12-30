package io.github.gfmois.labtcp.ui;

import io.github.gfmois.labtcp.app.AppSettings;
import io.github.gfmois.labtcp.service.AppSettingsService;
import io.github.gfmois.labtcp.ui.components.PathDialog;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Screen;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.Properties;

public class LabTCPApplication extends Application {
    private static final Logger logger = LogManager.getLogger(LabTCPApplication.class);
    private final AppSettingsService appSettingsService;

    public LabTCPApplication() {
        this.appSettingsService = new AppSettingsService();
    }

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("labtcp-view.fxml"));
        final Properties properties = getProperties();
        String version = properties.getProperty("version");

        Screen screen = Screen.getPrimary();
        Rectangle2D bounds = screen.getBounds();
        double width = bounds.getWidth() * 0.6;
        double height = bounds.getHeight() * 0.6;

        setAppIcon(stage);

        Scene scene = new Scene(fxmlLoader.load(), width, height);
        loadAppStyles(scene);
        stage.setTitle(String.format("Lab TCP - v%s", version));
        stage.setScene(scene);
        askForConfigurationsPathOrExit();
        stage.show();
    }

    private Properties getProperties() throws IOException {
        final Properties properties = new Properties();
        properties.load(getClass().getClassLoader().getResourceAsStream("application.properties"));
        return properties;
    }

    private void askForConfigurationsPathOrExit() {
        AppSettings settings = appSettingsService.getAppSettings();
        if (settings == null || settings.getConfigurationsPath() == null || settings.getConfigurationsPath().isEmpty()) {
            PathDialog setConfigurationsPathDialog = new PathDialog(settings);
            String selectedPath = setConfigurationsPathDialog.showAndWait().orElse(null);
            if (selectedPath == null || selectedPath.isEmpty()) {
                logger.info("User don't want to set configurations path, exiting application.");
                System.exit(0);
            }

            appSettingsService.save(selectedPath, null, true);
        }
    }

    public static void setAppIcon(Stage stage) throws IOException {
        InputStream iconStream = LabTCPApplication.class.getResourceAsStream("/favicon.png");
        if (iconStream == null) {
            throw new IOException("Icon resource not found");
        }
        stage.getIcons().add(new Image(iconStream));
    }

    private void loadAppStyles(Scene scene) {
        scene.getStylesheets().add(
                Objects.requireNonNull(getClass().getResource("styles/style.css")).toExternalForm()
        );
    }
}
