package dev.life.launcher.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Makes the AppImage a normal app: a menu entry (searchable in the app launcher) and icons in the
 * user's icon theme. Runs on every start from an AppImage, so moving the file just re-points it.
 */
public final class DesktopIntegration {
    private DesktopIntegration() {}

    public static void ensure() {
        String appimage = System.getenv("APPIMAGE");
        if (appimage == null || !System.getProperty("os.name", "").toLowerCase().contains("linux")) return;
        try {
            Path home = Path.of(System.getProperty("user.home"));
            Path data = System.getenv("XDG_DATA_HOME") != null ? Path.of(System.getenv("XDG_DATA_HOME")) : home.resolve(".local/share");
            writeIcons();
            String entry = "[Desktop Entry]\n"
                    + "Type=Application\n"
                    + "Name=Life Launcher\n"
                    + "GenericName=Minecraft Launcher\n"
                    + "Comment=Minecraft launcher for Life Client\n"
                    + "Exec=\"" + appimage.replace("\"", "\\\"") + "\" %U\n"
                    + "TryExec=" + appimage + "\n"
                    + "Icon=life-launcher\n"
                    + "Categories=Game;\n"
                    + "Keywords=minecraft;life;launcher;pvp;client;\n"
                    + "StartupWMClass=life-launcher\n"
                    + "Terminal=false\n";
            Path apps = data.resolve("applications");
            Files.createDirectories(apps);
            Path file = apps.resolve("life-launcher.desktop");
            if (!Files.exists(file) || !Files.readString(file).equals(entry)) {
                Files.writeString(file, entry);
                file.toFile().setExecutable(true);
                refresh(apps);
            }
        } catch (IOException ignored) {
            // not being in the menu is never worth failing start-up over
        }
    }

    private static Path dataDir() {
        Path home = Path.of(System.getProperty("user.home"));
        return System.getenv("XDG_DATA_HOME") != null ? Path.of(System.getenv("XDG_DATA_HOME")) : home.resolve(".local/share");
    }

    /**
     * Puts the app icon (your custom one, or Life's) into the user's icon theme, so the app menu and
     * taskbar show it. Called at start and whenever the icon is changed or reset.
     */
    public static void writeIcons() {
        if (!System.getProperty("os.name", "").toLowerCase().contains("linux")) return;
        Path data = dataDir();
        boolean changed = false;
        for (int size : new int[]{32, 48, 64, 128, 256, 512}) {
            try {
                java.awt.image.BufferedImage img = AppIcon.icon(size);
                if (img == null) continue;
                Path dir = data.resolve("icons/hicolor/" + size + "x" + size + "/apps");
                Files.createDirectories(dir);
                java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
                javax.imageio.ImageIO.write(img, "png", bytes);
                Path f = dir.resolve("life-launcher.png");
                if (!Files.exists(f) || !java.util.Arrays.equals(Files.readAllBytes(f), bytes.toByteArray())) {
                    Files.write(f, bytes.toByteArray());
                    changed = true;
                }
            } catch (IOException ignored) {}
        }
        if (changed) {
            try {   // nudge the icon caches so the new icon shows without logging out
                Files.setLastModifiedTime(data.resolve("icons/hicolor"), java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis()));
            } catch (IOException ignored) {}
            for (String[] cmd : new String[][]{{"gtk-update-icon-cache", "-f", "-t", data.resolve("icons/hicolor").toString()},
                    {"kbuildsycoca6", "--noincremental"}, {"kbuildsycoca5"}}) {
                try {
                    new ProcessBuilder(cmd).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
                } catch (IOException ignored) {}
            }
        }
    }

    private static void refresh(Path apps) {
        for (String[] cmd : new String[][]{{"update-desktop-database", apps.toString()}, {"kbuildsycoca6", "--noincremental"}, {"kbuildsycoca5"}}) {
            try {
                new ProcessBuilder(cmd).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            } catch (IOException ignored) {}
        }
    }
}
