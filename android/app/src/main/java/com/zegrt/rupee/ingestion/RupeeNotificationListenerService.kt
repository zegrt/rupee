package com.zegrt.rupee.ingestion

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class RupeeNotificationListenerService : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)
        // Real capture and parsing logic will be added in the ingestion slice.
    }
}
