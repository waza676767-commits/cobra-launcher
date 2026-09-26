package dev.cobra.launcher.ui;

import dev.cobra.launcher.core.Paths;

import java.awt.*;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * "Choose a file" everywhere in the launcher. On Windows it shows the normal Windows 10/11
 * file picker (Java's own looks like an old, odd one); elsewhere the system's native dialog.
 */
public final class FilePicker {
    private FilePicker() {}

    /** One file, or null when cancelled. */
    public static Path one(String title, String what, String... exts) {
        List<Path> l = pick(title, what, exts, false);
        return l.isEmpty() ? null : l.get(0);
    }

    /** Several files (empty when cancelled). */
    public static List<Path> many(String title, String what, String... exts) {
        return pick(title, what, exts, true);
    }

    private static List<Path> pick(String title, String what, String[] exts, boolean multi) {
        if ("windows".equals(Paths.OS_NAME)) {
            List<Path> r = windows(title, what, exts, multi);
            if (r != null) return r;
        }
        FileDialog fd = new FileDialog(MainWindow.get().frame, title, FileDialog.LOAD);
        fd.setMultipleMode(multi);
        if (exts.length > 0) {
            fd.setFilenameFilter((dir, name) -> {
                String n = name.toLowerCase();
                for (String e : exts) if (n.endsWith("." + e)) return true;
                return false;
            });
        }
        fd.setVisible(true);
        List<Path> out = new ArrayList<>();
        File[] files = fd.getFiles();
        if (files != null) for (File f : files) out.add(f.toPath());
        return out;
    }

    /** The modern Windows dialog through PowerShell (always present on Windows 10/11); null if it failed. */
    private static List<Path> windows(String title, String what, String[] exts, boolean multi) {
        try {
            StringBuilder pattern = new StringBuilder();
            for (String e : exts) pattern.append(pattern.length() == 0 ? "" : ";").append("*.").append(e);
            String filter = (what + " (" + (pattern.length() == 0 ? "*.*" : pattern) + ")|" + (pattern.length() == 0 ? "*.*" : pattern) + "|All files (*.*)|*.*")
                    .replace("'", "''");
            String script = "Add-Type -AssemblyName System.Windows.Forms;"
                    + "[System.Windows.Forms.Application]::EnableVisualStyles();"
                    + "$d=New-Object System.Windows.Forms.OpenFileDialog;"
                    + "$d.Title='" + title.replace("'", "''") + "';"
                    + "$d.Filter='" + filter + "';"
                    + "$d.Multiselect=$" + multi + ";"
                    + "$o=New-Object System.Windows.Forms.Form -Property @{TopMost=$true};"
                    + "if($d.ShowDialog($o) -eq 'OK'){[Console]::OutputEncoding=[Text.Encoding]::UTF8; $d.FileNames -join \"`n\"}";
            Process p = new ProcessBuilder("powershell.exe", "-NoProfile", "-STA", "-ExecutionPolicy", "Bypass", "-Command", script)
                    .redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (p.waitFor() != 0) return null;
            List<Path> r = new ArrayList<>();
            for (String line : out.split("\\r?\\n")) {
                if (line.isBlank()) continue;
                File f = new File(line.trim());
                if (f.isFile()) r.add(f.toPath());
            }
            return r;
        } catch (Exception e) {
            return null;
        }
    }
}
