package com.zegrt.rupee.ingestion

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
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

    private val normalizer: NotificationSignalNormalizer? by lazy {
        rupeeApp?.let { NotificationSignalNormalizer(it.database) }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "Listener connected")
    }

    override fun onListenerDisconnected() {
        Log.w(TAG, "Listener disconnected")
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)

        // Debug-only: capture every incoming notification's extras for the
        // real-world corpus we'll replay against parser v2.
        com.zegrt.rupee.diagnostics.NotificationDumper.dump(this, sbn)

        val isDebugMock = sbn.notification.extras
            ?.getBoolean(RupeeApplication.DEBUG_MOCK_EXTRA, false) == true

        // Group summaries duplicate content from their children — Android posts
        // both. Skip the summary explicitly so we don't double-count once the
        // extractor starts pulling EXTRA_TEXT_LINES (which can carry per-child
        // snippets and would trigger false-positive parses).
        if ((sbn.notification.flags and ExtractedNotification.FLAG_GROUP_SUMMARY) != 0) {
            if (com.zegrt.rupee.BuildConfig.DEBUG) {
                Log.d(TAG, "Skipped: group summary pkg=${sbn.packageName}")
            }
            return
        }

        if (sbn.packageName == packageName && !isDebugMock) {
            Log.d(TAG, "Skipped: self-package without mock extra")
            return
        }

        val extracted = NotificationExtractor.fromNotification(sbn.notification)

        if (com.zegrt.rupee.BuildConfig.DEBUG) {
            Log.d(
                TAG,
                "onPosted pkg=${sbn.packageName} mock=$isDebugMock " +
                    "body_len=${extracted.combinedBody.length} " +
                    "template=${extracted.template ?: "default"}",
            )
        }

        if (extracted.combinedBody.isBlank()) {
            Log.d(TAG, "Skipped: empty body after extraction (pkg=${sbn.packageName})")
            return
        }

        val normalizer = this.normalizer ?: run {
            Log.w(TAG, "Skipped: normalizer not initialized")
            return
        }

        serviceScope.launch {
            try {
                val rawEventId = normalizer.ingestNotification(
                    userId = "local-user",
                    packageName = sbn.packageName,
                    title = extracted.title,
                    body = extracted.combinedBody,
                    postedAtMillis = sbn.postTime,
                )
                if (rawEventId == null) {
                    Log.d(TAG, "Skipped: duplicate fingerprint already stored")
                } else {
                    Log.i(TAG, "Ingested raw event $rawEventId (pkg=${sbn.packageName})")
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
