package app.mathhelp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Reconnect after the phone restarts or the app updates. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        Relay.init(c);
        if (Store.hasUser()) { SyncJob.schedule(c); MsgService.start(c); }
    }
}
