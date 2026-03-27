package com.zegrt.rupee.ingestion

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.zegrt.rupee.RupeeApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class RupeeNotificationListenerService : NotificationListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)

        val extras = sbn.notification.extras
        val title = extras?.getCharSequence(NotificationCompat.EXTRA_TITLE)?.toString()?.trim()
        val body = extras?.getCharSequence(NotificationCompat.EXTRA_TEXT)?.toString()?.trim().orEmpty()

        if (body.isBlank()) return
        if (sbn.packageName == packageName) return

        val app = application as? RupeeApplication ?: return
        val writer = RawCaptureWriter(app.database)
        val normalizer = NotificationSignalNormalizer(app.database)

        serviceScope.launch {
            val rawEvent = writer.storeNotificationEvent(
                packageName = sbn.packageName,
                title = title,
                body = body,
                postedAtMillis = sbn.postTime,
            )
            if (rawEvent != null) {
                normalizer.normalize(rawEvent)
            }
        }
    }
}
