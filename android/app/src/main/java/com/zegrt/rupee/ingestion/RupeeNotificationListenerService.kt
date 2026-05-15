package com.zegrt.rupee.ingestion

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.zegrt.rupee.BuildConfig
import com.zegrt.rupee.RupeeApplication
import com.zegrt.rupee.diagnostics.NotificationDumper
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

        val isDebugMock = sbn.notification.extras
            ?.getBoolean(RupeeApplication.DEBUG_MOCK_EXTRA, false) == true

        // Listener-level filters first. Each one short-circuits to a single
        // dump-write with the filter reason so the dump file still captures
        // the raw extras (useful for inspecting what marketing notifs etc.
        // actually look like) but records that no DB writes happened.
        if ((sbn.notification.flags and ExtractedNotification.FLAG_GROUP_SUMMARY) != 0) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Skipped: group summary pkg=${sbn.packageName}")
            NotificationDumper.dump(
                this, sbn,
                IngestionResult.Filtered(IngestionResult.FilterReason.GROUP_SUMMARY),
            )
            return
        }

        if (sbn.packageName == packageName && !isDebugMock) {
            Log.d(TAG, "Skipped: self-package without mock extra")
            NotificationDumper.dump(
                this, sbn,
                IngestionResult.Filtered(IngestionResult.FilterReason.SELF_PACKAGE),
            )
            return
        }

        val extracted = NotificationExtractor.fromNotification(sbn.notification)

        if (BuildConfig.DEBUG) {
            Log.d(
                TAG,
                "onPosted pkg=${sbn.packageName} mock=$isDebugMock " +
                    "body_len=${extracted.combinedBody.length} " +
                    "template=${extracted.template ?: "default"}",
            )
        }

        if (extracted.combinedBody.isBlank()) {
            Log.d(TAG, "Skipped: empty body after extraction (pkg=${sbn.packageName})")
            NotificationDumper.dump(
                this, sbn,
                IngestionResult.Filtered(IngestionResult.FilterReason.EMPTY_BODY),
            )
            return
        }

        val normalizer = this.normalizer ?: run {
            Log.w(TAG, "Skipped: normalizer not initialized")
            NotificationDumper.dump(
                this, sbn,
                IngestionResult.Filtered(IngestionResult.FilterReason.NORMALIZER_UNAVAILABLE),
            )
            return
        }

        serviceScope.launch {
            val outcome = runCatching {
                normalizer.ingestNotification(
                    userId = "local-user",
                    packageName = sbn.packageName,
                    title = extracted.title,
                    body = extracted.combinedBody,
                    postedAtMillis = sbn.postTime,
                )
            }.getOrElse { t ->
                Log.e(TAG, "Failed to ingest notification from ${sbn.packageName}", t)
                IngestionResult.Filtered(IngestionResult.FilterReason.INGEST_FAILED)
            }

            // Log a brief outcome line for ad-hoc logcat debugging. The full
            // detail goes to the dump file below.
            when (outcome) {
                is IngestionResult.Filtered ->
                    Log.d(TAG, "Filtered (${outcome.reason})")
                is IngestionResult.GateRejected ->
                    Log.d(TAG, "Gate-rejected: ${outcome.gateReason} matched=${outcome.matched}")
                is IngestionResult.Ingested ->
                    Log.i(
                        TAG,
                        "Ingested ${outcome.rawEventId} parser=${outcome.parserKey} " +
                            "decision=${outcome.decisionState} candidate=${outcome.candidateId}",
                    )
            }

            // Dump-write happens AFTER the ingest transaction commits (or fails).
            // Keeps the DB write-lock hold time short and ensures the dump line
            // only reflects state that actually persisted.
            NotificationDumper.dump(this@RupeeNotificationListenerService, sbn, outcome)
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
