package dev.cobra.launcher.ui;

import dev.cobra.launcher.core.Paths;

import javax.swing.SwingUtilities;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;

/**
 * Watches the launcher's UI thread. If it doesn't answer for 2 seconds (a freeze), what it was
 * busy with is written to logs/launcher-freezes.log, so freezes can be tracked down and fixed.
 */
public final class FreezeWatch {
    private static volatile long lastPong = System.currentTimeMillis();
    private static Thread edt;

    private FreezeWatch() {}

    public static void start() {
        SwingUtilities.invokeLater(() -> edt = Thread.currentThread());
        Thread t = new Thread(() -> {
            boolean reported = false;
            while (true) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    return;
                }
                SwingUtilities.invokeLater(() -> lastPong = System.currentTimeMillis());
                long stuck = System.currentTimeMillis() - lastPong;
                if (stuck > 2000 && !reported && edt != null) {
                    reported = true;
                    report(stuck);
                } else if (stuck < 1000) {
                    reported = false;
                }
            }
        }, "cobra-freeze-watch");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }

    private static void report(long ms) {
        StringBuilder b = new StringBuilder();
        b.append(LocalDateTime.now()).append("  UI froze for ").append(ms).append(" ms, busy with:\n");
        for (StackTraceElement e : edt.getStackTrace()) b.append("    at ").append(e).append('\n');
        b.append('\n');
        try {
            Files.createDirectories(Paths.LOGS);
            Files.writeString(Paths.LOGS.resolve("launcher-freezes.log"), b.toString(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignored) {}
    }
}
