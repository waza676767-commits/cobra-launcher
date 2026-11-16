package app.mathhelp;

import android.app.Application;
import android.os.Build;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;

/** Saves any crash to a file so the next launch can show it (and the user can copy it). */
public class App extends Application {
    static File crashFile;

    @Override
    public void onCreate() {
        super.onCreate();
        crashFile = new File(getFilesDir(), "crash.txt");
        Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            try (PrintWriter w = new PrintWriter(new FileOutputStream(crashFile))) {
                w.println("mathhelp " + BuildConfigLite.VERSION + " on Android " + Build.VERSION.RELEASE
                        + " (API " + Build.VERSION.SDK_INT + "), " + Build.MANUFACTURER + " " + Build.MODEL);
                e.printStackTrace(w);
            } catch (Exception ignored) {}
            if (prev != null) prev.uncaughtException(t, e);
        });
    }

    static final class BuildConfigLite { static final String VERSION = "1.1"; }
}
