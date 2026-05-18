package com.zegrt.rupee.debug

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.zegrt.rupee.BuildConfig
import com.zegrt.rupee.RupeeApplication
import com.zegrt.rupee.data.local.entity.RawCaptureEventEntity
import com.zegrt.rupee.data.local.entity.RawCaptureIngestionStatus
import com.zegrt.rupee.data.local.entity.RawCaptureSourceType
import com.zegrt.rupee.data.local.entity.SyncStatus
import com.zegrt.rupee.data.repository.LocalFinanceRepository
import com.zegrt.rupee.diagnostics.CrashReporter
import com.zegrt.rupee.diagnostics.NotificationDumper
import com.zegrt.rupee.ingestion.NotificationParserRegistry
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SampleNotification(
    val label: String,
    val packageName: String,
    val title: String?,
    val body: String,
)

object DebugSamples {
    val gpay = SampleNotification(
        label = "GPay ₹245 to Swiggy",
        packageName = "com.google.android.apps.nbu.paisa.user",
        title = "₹245.00 paid",
        body = "You paid ₹245.00 to Swiggy using UPI",
    )
    val cred = SampleNotification(
        label = "CRED ₹1,499 Zomato",
        packageName = "com.dreamplug.androidapp",
        title = "Spent on HDFC card",
        body = "₹1,499 spent on HDFC Credit Card xx1234 at Zomato via CRED on 06 May",
    )
    val icici = SampleNotification(
        label = "ICICI ₹3,800 BigBasket",
        packageName = "com.csam.icici.bank.imobile",
        title = "Transaction alert",
        body = "Rs.3800.00 has been spent at BIGBASKET on ICICI Credit Card xx5678 on 07-MAY-26",
    )
    val phonepe = SampleNotification(
        label = "PhonePe ₹500 Swiggy",
        packageName = "com.phonepe.app",
        title = "Payment Successful",
        body = "₹500.00 sent to Swiggy via PhonePe",
    )
    val paytm = SampleNotification(
        label = "Paytm ₹1,499 Zomato",
        packageName = "net.one97.paytm",
        title = "Payment Successful",
        body = "₹1,499 paid to Zomato via Paytm UPI. UPI Ref: 412356789012",
    )

    val all: List<SampleNotification> = listOf(gpay, cred, icici, phonepe, paytm)
}

data class DebugUiState(
    val isResetting: Boolean = false,
    val sampleSent: String? = null,
    val parseInputTitle: String = "",
    val parseInputBody: String = "",
    val parseOutput: String? = null,
    val mockTitle: String = DebugSamples.gpay.title.orEmpty(),
    val mockBody: String = DebugSamples.gpay.body,
    val crashLogPreview: String = "",
    val notificationDumpSize: Long = 0L,
    val message: String? = null,
)

class DebugViewModel(
    private val repository: LocalFinanceRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DebugUiState())
    val uiState: StateFlow<DebugUiState> = _uiState.asStateFlow()

    private val parserRegistry = NotificationParserRegistry.default()

    fun wipeRawCapture() {
        viewModelScope.launch {
            repository.wipeRawCaptureData()
            _uiState.value = _uiState.value.copy(message = "Raw notification data wiped")
        }
    }

    fun refreshCrashLog(context: Context) {
        val log = CrashReporter.readLog(context)
        val preview = if (log.length > 1500) "…${log.takeLast(1500)}" else log
        _uiState.value = _uiState.value.copy(
            crashLogPreview = preview.ifBlank { "" },
            message = if (log.isBlank()) "No crashes logged." else null,
        )
    }

    fun clearCrashLog(context: Context) {
        CrashReporter.clearLog(context)
        _uiState.value = _uiState.value.copy(crashLogPreview = "", message = "Crash log cleared")
    }

    fun refreshNotificationDumpSize(context: Context) {
        _uiState.value = _uiState.value.copy(
            notificationDumpSize = NotificationDumper.fileSize(context),
        )
    }

    fun clearNotificationDumps(context: Context) {
        NotificationDumper.clear(context)
        _uiState.value = _uiState.value.copy(
            notificationDumpSize = 0L,
            message = "Notification dump cleared",
        )
    }

    fun shareNotificationDumps(context: Context) {
        val dir = NotificationDumper.directory(context) ?: run {
            _uiState.value = _uiState.value.copy(message = "Dump folder unavailable.")
            return
        }
        val source = java.io.File(dir, "dumps.jsonl").takeIf { it.exists() } ?: run {
            _uiState.value = _uiState.value.copy(message = "No dumps to share.")
            return
        }

        // Uniquely-named copies so multiple shares don't overwrite each other
        // in the recipient's downloads folder. Both files share a stamp so
        // dumps + outcomes can be paired by name.
        // Examples:
        //   rupee-notif-dumps-0.13.1-Pixel-7-20260513-104215.jsonl
        //   rupee-notif-outcomes-0.13.1-Pixel-7-20260513-104215.jsonl
        val device = "${Build.MANUFACTURER}-${Build.MODEL}"
            .replace(Regex("[^A-Za-z0-9-]"), "")
            .take(24)
        val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US)
            .apply { timeZone = java.util.TimeZone.getDefault() }
            .format(java.util.Date())
        val baseStem = "${BuildConfig.VERSION_NAME}-$device-$stamp"
        val dumpShareName = "rupee-notif-dumps-$baseStem.jsonl"
        val outcomesShareName = "rupee-notif-outcomes-$baseStem.jsonl"
        val sharedDumpFile = java.io.File(dir, dumpShareName)
        val sharedOutcomesFile = java.io.File(dir, outcomesShareName)
        // Phase 2a: do the file copy, snapshot build, and outcomes write off
        // the main thread. viewModelScope dispatches on Main.immediate so the
        // share intent at the end still fires on the UI thread as required.
        viewModelScope.launch {
            val copyResult: Result<Unit> = withContext(Dispatchers.IO) {
                // Tidy stale shared copies first so the folder doesn't accumulate.
                dir.listFiles { _, name ->
                    name.startsWith("rupee-notif-dumps-") || name.startsWith("rupee-notif-outcomes-")
                }?.forEach { it.delete() }
                runCatching { source.copyTo(sharedDumpFile, overwrite = true); Unit }
            }
            copyResult.exceptionOrNull()?.let { t ->
                Log.w(SHARE_TAG, "Dump copy failed", t)
                _uiState.value = _uiState.value.copy(
                    message = "Couldn't prepare dump for sharing (${shortReason(t)}).",
                )
                return@launch
            }
            // Read ids from the snapshotted copy, not the live dump, so any
            // notification that lands between the copy above and this parse
            // can't appear in outcomes-but-not-in-dump.
            val rawEventIds = withContext(Dispatchers.IO) {
                NotificationDumper.parseRawEventIdsFromDump(sharedDumpFile)
            }
            val outcomesUri: android.net.Uri? = if (rawEventIds.isNotEmpty()) {
                runCatching {
                    val snapshots = repository.getDumpOutcomeSnapshot(rawEventIds)
                    withContext(Dispatchers.IO) {
                        NotificationDumper.writeOutcomesFile(sharedOutcomesFile, snapshots)
                    }
                    androidx.core.content.FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        sharedOutcomesFile,
                    )
                }.onFailure { t ->
                    // Don't fail the whole share — we still attach the raw
                    // dump below via single-file ACTION_SEND. Just log so
                    // the cause is in logcat for debugging.
                    Log.w(SHARE_TAG, "Outcomes snapshot build failed; sharing dump-only", t)
                }.getOrNull()
            } else {
                null
            }

            val dumpUri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                sharedDumpFile,
            )

            // ACTION_SEND_MULTIPLE so the user sees both files in the chooser.
            // Falls back to ACTION_SEND for dump-only when outcomes failed to
            // build — a share that loses the dump because the snapshot blew up
            // would be a regression.
            val intent = if (outcomesUri != null) {
                Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "application/json"
                    putParcelableArrayListExtra(
                        Intent.EXTRA_STREAM,
                        arrayListOf(dumpUri, outcomesUri),
                    )
                    putExtra(Intent.EXTRA_SUBJECT, "Rupee notification dump + outcomes — $baseStem")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } else {
                Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_STREAM, dumpUri)
                    putExtra(Intent.EXTRA_SUBJECT, "Rupee notification dump — $dumpShareName")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            runCatching {
                context.startActivity(
                    Intent.createChooser(intent, "Share dumps").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }.onFailure { t ->
                Log.w(SHARE_TAG, "Share intent failed", t)
                _uiState.value = _uiState.value.copy(
                    message = "Couldn't launch share (${shortReason(t)}).",
                )
            }
        }
    }

    fun emailCrashLog(context: Context) {
        val log = CrashReporter.readLog(context)
        if (log.isBlank()) {
            _uiState.value = _uiState.value.copy(message = "No crashes to email.")
            return
        }
        val intent = Intent(Intent.ACTION_SENDTO, "mailto:studioxero.biz@gmail.com".toUri()).apply {
            putExtra(Intent.EXTRA_SUBJECT, "Rupee crash log")
            // Tail the log to fit a sane email body.
            val excerpt = if (log.length > 100_000) "…${log.takeLast(100_000)}" else log
            putExtra(Intent.EXTRA_TEXT, excerpt)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching {
            context.startActivity(Intent.createChooser(intent, "Email crash log").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure {
            _uiState.value = _uiState.value.copy(message = "No email app installed.")
        }
    }

    fun resetAllData() {
        _uiState.value = _uiState.value.copy(isResetting = true)
        viewModelScope.launch {
            repository.resetAllData()
            _uiState.value = _uiState.value.copy(
                isResetting = false,
                message = "App data reset",
            )
        }
    }

    fun sendSample(sample: SampleNotification) {
        viewModelScope.launch {
            repository.debugIngestNotification(
                packageName = sample.packageName,
                title = sample.title,
                body = sample.body,
            )
            _uiState.value = _uiState.value.copy(
                sampleSent = sample.label,
                message = "Pushed: ${sample.label}",
            )
        }
    }

    fun updateParseTitle(value: String) {
        _uiState.value = _uiState.value.copy(parseInputTitle = value)
    }

    fun updateParseBody(value: String) {
        _uiState.value = _uiState.value.copy(parseInputBody = value)
    }

    fun runParseTest(packageName: String = "com.example.test") {
        val state = _uiState.value
        val now = Instant.now().toString()
        val raw = RawCaptureEventEntity(
            id = "debug-parse-${System.nanoTime()}",
            userId = "local-user",
            sourceType = RawCaptureSourceType.NOTIFICATION,
            sourceAppPackage = packageName,
            title = state.parseInputTitle.ifBlank { null },
            body = state.parseInputBody,
            receivedAt = now,
            deviceEventTime = now,
            hashFingerprint = "debug-parse-${System.nanoTime()}",
            ingestionStatus = RawCaptureIngestionStatus.CAPTURED,
            createdAt = now,
            updatedAt = now,
            syncStatus = SyncStatus.LOCAL_ONLY,
        )
        val result = parserRegistry.parse(raw)
        val pretty = buildString {
            appendLine("parserKey: ${result.parserKey}")
            appendLine("providerHint: ${result.providerHint ?: "—"}")
            appendLine("transactionKind: ${result.transactionKind}")
            appendLine("candidateType: ${result.candidateType}")
            appendLine("amountMinor: ${result.amountMinor ?: "—"}")
            appendLine("currency: ${result.currencyCode ?: "—"}")
            appendLine("merchantRaw: ${result.merchantRaw ?: "—"}")
            appendLine("toEntityName: ${result.toEntityName ?: "—"}")
            appendLine("mode: ${result.mode ?: "—"}")
            appendLine("maskedDigits: ${result.maskedDigits ?: "—"}")
            append("parseConfidence: ${result.parseConfidence}")
        }
        _uiState.value = state.copy(parseOutput = pretty)
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    fun updateMockTitle(value: String) {
        _uiState.value = _uiState.value.copy(mockTitle = value)
    }

    fun updateMockBody(value: String) {
        _uiState.value = _uiState.value.copy(mockBody = value)
    }

    fun loadMockFromSample(sample: SampleNotification) {
        _uiState.value = _uiState.value.copy(
            mockTitle = sample.title.orEmpty(),
            mockBody = sample.body,
        )
    }

    fun postMockNotification(context: Context) {
        val state = _uiState.value
        if (state.mockBody.isBlank()) {
            _uiState.value = state.copy(message = "Body is required")
            return
        }
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm == null) {
            _uiState.value = state.copy(message = "NotificationManager unavailable")
            return
        }
        val extras = Bundle().apply { putBoolean(RupeeApplication.DEBUG_MOCK_EXTRA, true) }
        val notification = NotificationCompat.Builder(context, RupeeApplication.DEBUG_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_def_app_icon)
            .setContentTitle(state.mockTitle.ifBlank { "Rupee mock" })
            .setContentText(state.mockBody)
            .addExtras(extras)
            .setAutoCancel(true)
            .build()
        nm.notify(MOCK_NOTIFICATION_ID, notification)
        _uiState.value = state.copy(
            message = "Posted. Filter logcat for 'RupeeNotifListener' to confirm the service fired.",
        )
    }

    /**
     * Single-line failure summary for the user-facing toast. Trims the
     * exception message because some IOException messages dump entire
     * file paths and would blow up the snackbar. Pair with a logcat warn
     * call (tag = SHARE_TAG) for full detail.
     */
    private fun shortReason(t: Throwable): String {
        val msg = t.message.orEmpty().take(60).ifBlank { "no detail" }
        return "${t.javaClass.simpleName}: $msg"
    }

    companion object {
        private const val MOCK_NOTIFICATION_ID = 4001

        /**
         * Logcat tag for the dump-share flow. Lets a user grep logcat for
         * "RupeeShare" when the share-dump button reports a failure they
         * can't otherwise diagnose.
         */
        private const val SHARE_TAG = "RupeeShare"
    }
}

class DebugViewModelFactory(
    private val repository: LocalFinanceRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(DebugViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }
        return DebugViewModel(repository) as T
    }
}
