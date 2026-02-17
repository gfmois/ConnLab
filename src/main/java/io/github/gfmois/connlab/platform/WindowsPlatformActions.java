package io.github.gfmois.connlab.platform;

import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

public class WindowsPlatformActions implements PlatformActions {
    private final Desktop desktop;

    public WindowsPlatformActions() {
        if (!Desktop.isDesktopSupported()) {
            throw new UnsupportedOperationException("Desktop API not supported on this environment");
        }
        this.desktop = Desktop.getDesktop();
        if (!desktop.isSupported(Desktop.Action.OPEN)) {
            throw new UnsupportedOperationException("Desktop OPEN action not supported");
        }
    }

    @Override
    public void openFileExplorer(Path path) throws IOException {
        if (path == null) throw new NullPointerException("path");
        Path abs = path.toAbsolutePath().normalize();

        if (!Files.exists(abs)) {
            throw new IllegalArgumentException("Path does not exists: " + abs);
        }

        Path target = Files.isDirectory(abs) ? abs : abs.getParent();
        if (target == null) {
            throw new IllegalArgumentException("Path has no parent directory: " + abs);
        }

        desktop.open(target.toFile());
    }

    @Override
    public void openUrl(URI uri) throws IOException {
        PlatformActions.super.openUrl(uri);
    }
}
