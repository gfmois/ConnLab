package io.github.gfmois.connlab.ui;

import io.github.gfmois.connlab.app.AppSettings;
import io.github.gfmois.connlab.service.AppSettingsService;
import io.github.gfmois.connlab.ui.components.PathDialog;
import io.github.gfmois.connlab.ui.controller.ConnLabController;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

public class ConnLabApplication extends Application {
    private static final Logger logger = LogManager.getLogger(ConnLabApplication.class);
    private final AppSettingsService appSettingsService;

    public ConnLabApplication() {
        this.appSettingsService = new AppSettingsService();
    }

    @Override
    public void start(Stage stage) throws IOException {
        Theme.apply();

        boolean removeDb = Optional.of(Boolean.parseBoolean(System.getProperty("removeDb"))).orElse(false);
        if (removeDb) {
            appSettingsService.removeAppConfiguration();
        }

        askForConfigurationsPathOrExit();

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

        ConnLabController controller = fxmlLoader.getController();
        stage.setOnCloseRequest(event -> {
            if (!controller.confirmUnsavedChanges()) {
                event.consume();
            }
        });
        stage.setMinWidth(900);
        stage.setMinHeight(600);
        stage.setTitle(String.format("ConnLab - v%s", version));
        stage.setScene(scene);
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
            createDefaultConfiguration(selectedPath);
        }
    }

    /**
     * Copies from the resources folder the `default.json` file to the selected path
     */
    private void createDefaultConfiguration(String selectedPath) {
        try (InputStream defaultConfiguration = getClass().getClassLoader().getResourceAsStream("default.json")) {
            logger.info("Retrieved defaultConfiguration");
            if (defaultConfiguration == null) {
                throw new IllegalStateException("Critical resource 'default.json' is missing from the application bundle.");
            }

            Files.copy(defaultConfiguration, Path.of(String.format("%s/default.json", selectedPath)), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            logger.error("Error while trying to recover default.json", e);
            throw new RuntimeException(e);
        }
    }

    public static void setAppIcon(Stage stage) throws IOException {
        InputStream iconStream = ConnLabApplication.class.getResourceAsStream("/favicon.png");
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
