package dev.life.launcher;

import dev.life.launcher.core.Paths;
import dev.life.launcher.ui.MainWindow;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Field;

public final class LifeLauncher {
    private LifeLauncher() {}

    public static void main(String[] args) throws Exception {
        Paths.init();
        // a newer launcher was downloaded last time: run that one instead (the update is "reopen")
        if (dev.life.launcher.core.Updater.handOver(args)) return;
        dev.life.launcher.core.Legacy.migrate();      // files from the launcher's old names, once
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");
        // One back buffer per window + a shaped, undecorated frame on X11/XWayland repaints regions at
        // the wrong offset (panels drawn shifted and clipped, stale copies left behind). Plain
        // per-paint double buffering avoids that.
        System.setProperty("swing.bufferPerWindow", System.getProperty("swing.bufferPerWindow", "false"));
        // XRender on XWayland (KDE / Nobara) blits repaint regions to the wrong place when the window
        // is shaped and panels are translucent: copies of pages pile up on screen. The plain X11
        // pipeline doesn't.
        System.setProperty("sun.java2d.xrender", System.getProperty("sun.java2d.xrender", "false"));
        System.setProperty("sun.java2d.pmoffscreen", System.getProperty("sun.java2d.pmoffscreen", "false"));
        System.setProperty("sun.java2d.opengl", System.getProperty("sun.java2d.opengl", "false"));
        setWmClass("life-launcher");
        new Thread(dev.life.launcher.core.DesktopIntegration::ensure, "life-desktop").start();
        UIManager.put("ToolTip.background", new Color(0x1e1f22));
        SwingUtilities.invokeLater(() -> {
            // Repaint the whole window whenever anything changes. Translucent glass over a (video)
            // wallpaper means every part depends on what's behind it; partial repaints were where
            // the ghost copies came from. Everything is cached, so a full repaint is just blits.
            RepaintManager.setCurrentManager(new RepaintManager() {
                @Override
                public void addDirtyRegion(JComponent c, int x, int y, int w, int h) {
                    JRootPane rp = SwingUtilities.getRootPane(c);
                    if (rp != null && rp != c && rp.getWidth() > 0) super.addDirtyRegion(rp, 0, 0, rp.getWidth(), rp.getHeight());
                    else super.addDirtyRegion(c, x, y, w, h);
                }
            });
            new MainWindow().show();
            dev.life.launcher.core.DiscordPresence.idle();
            dev.life.launcher.core.DiscordPresence.start();
            dev.life.launcher.core.Updater.checkInBackground(build -> SwingUtilities.invokeLater(() ->
                    MainWindow.get().toast("Life update ready. Close and reopen the launcher to get it.")));
            new Thread(() -> {   // Life Client ready in the launcher's folder before the first launch
                try {
                    dev.life.launcher.game.Installer.ensureClient(dev.life.launcher.game.GameVersion.MODERN);
                } catch (Exception ignored) {}
            }, "life-client-setup").start();
            {
                // ffmpeg (video wallpapers, Screen Recorder): fetched once in the background on every
                // system that doesn't have it yet, so it's simply there
                Thread t = new Thread(() -> {
                    try {
                        dev.life.launcher.core.FFmpeg.ensure(null);
                    } catch (Exception ignored) {}
                }, "life-ffmpeg");
                t.setDaemon(true);
                t.setPriority(Thread.MIN_PRIORITY);
                t.start();
            }
        });
    }

    /** Lets the desktop match the window to life-launcher.desktop (taskbar icon grouping). */
    private static void setWmClass(String name) {
        try {
            Toolkit tk = Toolkit.getDefaultToolkit();
            Field f = tk.getClass().getDeclaredField("awtAppClassName");
            f.setAccessible(true);
            f.set(tk, name);
        } catch (Throwable ignored) {
            // needs --add-opens java.desktop/sun.awt.X11=ALL-UNNAMED; harmless without it
        }
    }
}
