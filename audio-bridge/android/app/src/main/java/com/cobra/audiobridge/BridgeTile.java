package com.cobra.audiobridge;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** Quick-settings tile: pull down the notification shade and tap "Audio Bridge" to turn it on/off. */
public class BridgeTile extends TileService {
    @Override public void onStartListening() { update(); }

    @Override
    @SuppressWarnings("deprecation")
    public void onClick() {
        if (BridgeService.running) {
            stopService(new Intent(this, BridgeService.class));
            BridgeService.running = false;
            update();
            return;
        }
        // Android only lets an app start the mic from the foreground, so open the app; it starts itself.
        Intent i = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(MainActivity.EXTRA_START, true);
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, i,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        } else {
            startActivityAndCollapse(i);
        }
    }

    private void update() {
        Tile t = getQsTile();
        if (t == null) return;
        t.setState(BridgeService.running ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        t.updateTile();
    }
}
