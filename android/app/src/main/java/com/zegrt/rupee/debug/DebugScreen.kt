package com.zegrt.rupee.debug

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
) {
    var resetConfirmOpen by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("Debug tools", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "Internal-only. These actions touch the local database directly.",
            style = MaterialTheme.typography.bodyMedium,
        )
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
        AlertDialog(
            onDismissRequest = { resetConfirmOpen = false },
            title = { Text("Reset app data?") },
            text = { Text("This wipes the local database and re-seeds defaults. There is no undo.") },
            confirmButton = {
                TextButton(onClick = {
                    resetConfirmOpen = false
                    onReset()
                }) {
                    Text("Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { resetConfirmOpen = false }) { Text("Cancel") }
            },
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
