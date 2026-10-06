package dev.cobra.launcher.core;

import java.io.InputStream;
import java.util.Properties;

public final class BuildInfo {
    public static final String NAME = "Abyss Launcher";
    public static final String VERSION;
    /** Paste your Azure client id into launcher/src/main/resources/data/cobra.properties (or Settings → Account). */
    public static final String CLIENT_ID;

    static {
        Properties p = new Properties();
        try (InputStream in = BuildInfo.class.getResourceAsStream("/data/cobra.properties")) {
            if (in != null) p.load(in);
        } catch (Exception ignored) {}
        VERSION = p.getProperty("version", "1.0.0");
        CLIENT_ID = p.getProperty("clientId", "").trim();
    }

    private BuildInfo() {}
}
