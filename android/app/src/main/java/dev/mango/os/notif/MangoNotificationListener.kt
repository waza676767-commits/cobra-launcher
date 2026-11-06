package dev.mango.os.notif

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dev.mango.os.data.NotificationHub

/**
 * Gives Mango the phone's notifications (lock screen list, icon badges, now playing).
 * The system keeps it bound once the user allows "Notification access"; it does no work of its own.
 */
class MangoNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() {
        NotificationHub.attach(this)
    }

    override fun onListenerDisconnected() {
        NotificationHub.detach()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        NotificationHub.posted(sbn)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap?) {
        NotificationHub.posted(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        NotificationHub.removed(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap?) {
        NotificationHub.removed(sbn)
    }
}
