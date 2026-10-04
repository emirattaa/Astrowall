package com.example.strictmode

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/** Süresi dolan uygulamaların bildirimlerini anında siler. */
class NotifBlocker : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (Prefs.blocked(this, sbn.packageName)) cancelNotification(sbn.key)
    }
}
