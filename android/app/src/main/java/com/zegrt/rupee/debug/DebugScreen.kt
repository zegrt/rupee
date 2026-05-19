package com.zegrt.rupee.debug

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun DebugScreen(
    state: DebugUiState,
    onReset: () -> Unit,
    onSendSample: (SampleNotification) -> Unit,
    onUpdateParseTitle: (String) -> Unit,
    onUpdateParseBody: (String) -> Unit,
    onRunParseTest: () -> Unit,
    onPostMockNotification: () -> Unit = {},
    onUpdateMockTitle: (String) -> Unit = {},
    onUpdateMockBody: (String) -> Unit = {},
    onLoadMockSample: (SampleNotification) -> Unit = {},
    onWipeRawCapture: () -> Unit = {},
    onViewCrashLog: () -> Unit = {},
    onEmailCrashLog: () -> Unit = {},
    onClearCrashLog: () -> Unit = {},
    crashLogPreview: String = "",
    onShareNotificationDumps: () -> Unit = {},
    onClearNotificationDumps: () -> Unit = {},
    notificationDumpSize: Long = 0L,
    ingestionHealth: com.zegrt.rupee.data.local.dao.IngestionHealth? = null,
) {
    var resetConfirmOpen by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("Debug tools", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "Internal-only. These actions touch the local database directly.",
            style = MaterialTheme.typography.bodyMedium,
        )
        ingestionHealth?.let { IngestionHealthCard(it) }
        DebugCard(title = "Send sample notification") {
            Text(
                "Pushes a synthetic notification through the real ingestion pipeline (writer → parser → dedupe → decision).",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(modifier = Modifier.height(12.dp))
            DebugSamples.all.forEach { sample ->
                OutlinedButton(
                    onClick = { onSendSample(sample) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(sample.label)
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            state.sampleSent?.let {
                Text("Last sent: $it", style = MaterialTheme.typography.labelMedium)
            }
        }
        DebugCard(title = "Post real system notification") {
            Text(
                "Posts an actual Android notification (with a sentinel extra) so the listener service runs end-to-end. Edit the title and body below before posting. Run `adb logcat -s RupeeNotifListener` to confirm the listener fired.",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text("Load from a sample", style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(6.dp))
            DebugSamples.all.forEach { sample ->
                OutlinedButton(
                    onClick = { onLoadMockSample(sample) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(sample.label)
                }
                Spacer(modifier = Modifier.height(6.dp))
            }
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = state.mockTitle,
                onValueChange = onUpdateMockTitle,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Title") },
                singleLine = true,
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = state.mockBody,
                onValueChange = onUpdateMockBody,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Body") },
                minLines = 3,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onPostMockNotification,
                modifier = Modifier.fillMaxWidth(),
                enabled = state.mockBody.isNotBlank(),
            ) {
                Text("Post mock notification (real path)")
            }
        }
        DebugCard(title = "Parser playground") {
            Text(
                "Paste a notification body to see how the parser registry interprets it. Does NOT touch the DB.",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = state.parseInputTitle,
                onValueChange = onUpdateParseTitle,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Title (optional)") },
                singleLine = true,
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = state.parseInputBody,
                onValueChange = onUpdateParseBody,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Body") },
                minLines = 3,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onRunParseTest,
                enabled = state.parseInputBody.isNotBlank(),
            ) {
                Text("Parse")
            }
            state.parseOutput?.let { output ->
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Text(
                        text = output,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        DebugCard(title = "Crash log") {
            Text(
                "Uncaught exceptions are appended to a local file. Email it to the dev or clear it after triage.",
                style = MaterialTheme.typography.bodySmall,
            )
            if (crashLogPreview.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Text(
                        crashLogPreview,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onEmailCrashLog) { Text("Email log") }
                OutlinedButton(onClick = onViewCrashLog) { Text("Refresh") }
                OutlinedButton(onClick = onClearCrashLog) { Text("Clear") }
            }
        }
        DebugCard(title = "Notification dumps") {
            Text(
                "Debug-only: every incoming notification's extras are saved to a JSONL " +
                    "file. We use this corpus to test the JSON rule engine against real-world " +
                    "bodies. Email the file to the dev or clear when triaged.",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(modifier = Modifier.height(8.dp))
            val sizeLabel = when {
                notificationDumpSize <= 0L -> "No dumps captured yet."
                notificationDumpSize < 1024 -> "$notificationDumpSize bytes"
                notificationDumpSize < 1024 * 1024 -> "${notificationDumpSize / 1024} KB"
                else -> "${notificationDumpSize / 1024 / 1024} MB"
            }
            Text(sizeLabel, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onShareNotificationDumps,
                    enabled = notificationDumpSize > 0L,
                ) { Text("Share dump file") }
                OutlinedButton(
                    onClick = onClearNotificationDumps,
                    enabled = notificationDumpSize > 0L,
                ) { Text("Clear") }
            }
        }
        DebugCard(title = "Wipe raw capture") {
            Text(
                "Deletes every notification body Rupee has stored in raw_capture_events. " +
                    "Keeps your transactions, budgets, and trust rules intact.",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(onClick = onWipeRawCapture) { Text("Wipe raw notification data") }
        }
        DebugCard(title = "Danger zone") {
            Text(
                "Reset wipes the entire local database (transactions, candidates, raw events, inbox items, user, accounts, cards, budget) and re-seeds default categories, buckets, user, and a fresh monthly budget for the current month. There is no undo.",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { resetConfirmOpen = true },
                enabled = !state.isResetting,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) {
                Text(if (state.isResetting) "Resetting..." else "Reset app data")
            }
        }
        state.message?.let {
            Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
    }

    if (resetConfirmOpen) {
        var typed by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { resetConfirmOpen = false },
            title = { Text("Reset app data?") },
            text = {
                Column {
                    Text("This wipes the local database and re-seeds defaults. There is no undo.")
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Type WIPE to confirm.",
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = typed,
                        onValueChange = { typed = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        resetConfirmOpen = false
                        onReset()
                    },
                    enabled = typed.trim() == "WIPE",
                ) {
                    Text("Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { resetConfirmOpen = false }) { Text("Cancel") }
            },
        )
    }
}

/**
 * T3 — ingestion health card.
 *
 * Renders the 7-day funnel: total notifications received by the listener,
 * how many were successfully ingested as transaction candidates, how many
 * the gate rejected as non-transactional, and how many threw exceptions
 * mid-pipeline. A non-zero failure count paints the card's accent loud
 * red — the whole reason this surface exists is so silent regressions
 * like the v0.14.0 H4 FK bug become *visible* on-device.
 *
 * Compact numerical layout to keep the Debug screen scannable. Not a
 * pretty chart — this is internal diagnostics, not user-facing analytics.
 */
@Composable
private fun IngestionHealthCard(health: com.zegrt.rupee.data.local.dao.IngestionHealth) {
    val accent = if (health.hasFailures) MaterialTheme.colorScheme.error
    else MaterialTheme.colorScheme.primary
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = if (health.hasFailures) 2.dp else 1.dp,
            color = if (health.hasFailures) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Ingestion health · last ${health.windowDays}d",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                if (health.hasFailures) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "⚠ failures",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "${health.totalReceived} notifications reached the parser pipeline.",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(modifier = Modifier.height(12.dp))
            HealthRow(
                label = "Ingested as transaction candidates",
                count = health.ingestedCount,
                total = health.totalReceived,
                accent = accent,
            )
            HealthRow(
                label = "Gate-rejected (non-transactional)",
                count = health.gateRejectedCount,
                total = health.totalReceived,
                accent = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HealthRow(
                label = "Failed (exception during ingest)",
                count = health.failedCount,
                total = health.totalReceived,
                accent = if (health.failedCount > 0) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (health.inFlightCount > 0) {
                HealthRow(
                    label = "In-flight (still processing)",
                    count = health.inFlightCount,
                    total = health.totalReceived,
                    accent = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (health.hasFailures) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Failures mean an exception fired inside `normalizeLocked`. " +
                        "Share the notification dump (button below) — the dump's " +
                        "`outcome.errorClass` field carries the exception name.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun HealthRow(label: String, count: Int, total: Int, accent: androidx.compose.ui.graphics.Color) {
    val pct = if (total == 0) 0 else (count * 100 / total)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            "$count ($pct%)",
            style = MaterialTheme.typography.bodyMedium,
            color = accent,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun DebugCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}
