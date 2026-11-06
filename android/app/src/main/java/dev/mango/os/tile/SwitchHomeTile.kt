package dev.mango.os.tile

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import dev.mango.os.home.HomeSwitch

/** Quick Settings tile: opens the "Default home app" picker so you can flip between Mango and One UI Home. */
class SwitchHomeTile : TileService() {
    override fun onStartListening() {
        qsTile?.let {
            it.state = if (HomeSwitch.isDefault(this)) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            it.subtitle = if (HomeSwitch.isDefault(this)) "Mango" else "One UI Home"
            it.updateTile()
        }
    }

    override fun onClick() {
        val intent = Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            val pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            startActivityAndCollapse(pi)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
