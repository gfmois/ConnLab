package io.github.gfmois.connlab.platform;

public class PlatformFactory {
    private PlatformFactory() {
    }

    public static PlatformActions create() {
        String os = System.getProperty("os.name").toLowerCase();

        if (os.contains("win")) return new WindowsPlatformActions();
        if (os.contains("mac") || os.contains("darwin")) return new MacPlatformActions();
        if (os.contains("nix") || os.contains("nux") || os.contains("aix")) return new LinuxPlatformActions();

        throw new UnsupportedOperationException("Unsupported OS: " + os);
    }
}
