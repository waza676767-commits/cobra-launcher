package app.mathhelp;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

/** Keeps one open connection to the relay so messages arrive instantly, even with the app closed. */
public class MsgService extends Service {
    static void start(Context c) {
        try { c.startForegroundService(new Intent(c, MsgService.class)); }
        catch (Exception ignored) { /* not allowed right now (e.g. from background); the 15-min job still runs */ }
    }

    @Override
    public int onStartCommand(Intent i, int flags, int id) {
        Relay.init(this);
        PendingIntent pi = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, "s")
                .setSmallIcon(R.drawable.ic)
                .setContentTitle("mathhelp")
                .setContentText("Connected — messages arrive instantly")
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
        try {
            if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            else startForeground(1, n);
        } catch (Exception e) {
            stopSelf();
            return START_NOT_STICKY;
        }
        Relay.start();
        return START_STICKY;
    }

    @Override public IBinder onBind(Intent i) { return null; }
}
