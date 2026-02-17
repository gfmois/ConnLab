package io.github.gfmois.connlab.platform;

import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class LinuxPlatformActions implements PlatformActions {

    @Override
    public void openFileExplorer(Path path) throws IOException {
        if (path == null) throw new NullPointerException("path");

        Path abs = path.toAbsolutePath().normalize();
        if (!Files.exists(abs)) {
            throw new IllegalArgumentException("Path does not exist: " + abs);
        }

        Path target = Files.isDirectory(abs) ? abs : abs.getParent();
        if (target == null) {
            throw new IllegalArgumentException("Path has no parent directory: " + abs);
        }

        if (Desktop.isDesktopSupported()) {
            Desktop desktop = Desktop.getDesktop();
            if (desktop.isSupported(Desktop.Action.OPEN)) {
                try {
                    desktop.open(target.toFile());
                    return;
                } catch (Exception ignored) {
                }
            }
        }

        new ProcessBuilder("xdg-open", target.toString())
                .redirectErrorStream(true)
                .start();
    }
}
