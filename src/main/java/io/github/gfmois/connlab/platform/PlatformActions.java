package io.github.gfmois.connlab.platform;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;

public interface PlatformActions {
    void openFileExplorer(Path path) throws IOException;

    default void openUrl(URI uri) throws IOException {
        throw new UnsupportedOperationException("openUrl not implemented");
    }
}
