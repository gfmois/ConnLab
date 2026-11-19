package io.github.gfmois.connlab.ui;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import javafx.application.Application;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.prefs.Preferences;

/**
 * The AtlantaFX theme of the application, light or dark; the choice is remembered between runs.
 */
public final class Theme {
    private static final Logger logger = LogManager.getLogger(Theme.class);
    private static final String DARK_KEY = "darkTheme";
    private static final BooleanProperty dark = new SimpleBooleanProperty(readDark());

    static {
        dark.addListener((obs, old, isDark) -> {
            apply();
            try {
                preferences().putBoolean(DARK_KEY, isDark);
            } catch (RuntimeException e) {
                logger.warn("Could not remember the theme", e);
            }
        });
    }

    private Theme() {
    }

    /**
     * Applies the current theme to every window.
     */
    public static void apply() {
        Application.setUserAgentStylesheet(dark.get()
                ? new PrimerDark().getUserAgentStylesheet()
                : new PrimerLight().getUserAgentStylesheet());
    }

    /**
     * Whether the dark theme is used; changing it applies the theme straight away.
     */
    public static BooleanProperty darkProperty() {
        return dark;
    }

    private static boolean readDark() {
        try {
            return preferences().getBoolean(DARK_KEY, false);
        } catch (RuntimeException e) {
            logger.warn("Could not read the theme preference", e);
            return false;
        }
    }

    private static Preferences preferences() {
        return Preferences.userNodeForPackage(Theme.class);
    }
}
