package com.zegrt.rupee.ingestion

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import com.zegrt.rupee.RupeeApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class RupeeNotificationListenerService : NotificationListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val rupeeApp: RupeeApplication?
        get() = application as? RupeeApplication

    private val writer: RawCaptureWriter? by lazy { rupeeApp?.let { RawCaptureWriter(it.database) } }
    private val normalizer: NotificationSignalNormalizer? by lazy {
        rupeeApp?.let { NotificationSignalNormalizer(it.database) }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)

        val extras = sbn.notification.extras
        val title = extras?.getCharSequence(NotificationCompat.EXTRA_TITLE)?.toString()?.trim()
        val body = extras?.getCharSequence(NotificationCompat.EXTRA_TEXT)?.toString()?.trim().orEmpty()

        if (body.isBlank()) return

        val isDebugMock = sbn.notification.extras
            ?.getBoolean(RupeeApplication.DEBUG_MOCK_EXTRA, false) == true
        if (sbn.packageName == packageName && !isDebugMock) return

        val writer = this.writer ?: return
        val normalizer = this.normalizer ?: return

        serviceScope.launch {
            try {
                val rawEvent = writer.storeNotificationEvent(
                    packageName = sbn.packageName,
                    title = title,
                    body = body,
                    postedAtMillis = sbn.postTime,
                )
                if (rawEvent != null) {
                    normalizer.normalize(rawEvent)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to ingest notification from ${sbn.packageName}", t)
            }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "RupeeNotifListener"
    }
}
