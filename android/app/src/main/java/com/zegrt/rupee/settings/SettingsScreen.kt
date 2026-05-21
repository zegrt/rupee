package com.zegrt.rupee.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onNameDraftChange: (String) -> Unit,
    onSaveName: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onRequestPostNotifications: () -> Unit,
    onOpenTrustRules: () -> Unit,
    onOpenCardsEmis: () -> Unit,
    onOpenBudgets: () -> Unit,
    onOpenRecurring: () -> Unit,
    onOpenRecap: () -> Unit,
    onSendFeedback: () -> Unit,
    onOpenDebug: () -> Unit,
    onExportLedgerCsv: () -> Unit,
    onExportLedgerJson: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        SettingsCard(title = "Profile") {
            OutlinedTextField(
                value = state.displayNameDraft,
                onValueChange = onNameDraftChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Display name") },
                singleLine = true,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onSaveName,
                enabled = !state.savingName && state.displayNameDraft.isNotBlank() && state.displayNameDraft != state.displayName,
            ) {
                Text(if (state.savingName) "Saving..." else "Save name")
            }
        }
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenBudgets),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Budgets", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "Monthly: ${state.monthlyBudgetLabel}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Text("›", style = MaterialTheme.typography.headlineSmall)
            }
        }
        SettingsCard(title = "Notifications") {
            Text(
                text = if (state.notificationGranted) "Notification access is granted." else "Notification access is required for auto-tracking.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(onClick = onOpenNotificationSettings) {
                Text(if (state.notificationGranted) "Manage permission" else "Grant access")
            }
            if (state.needsPostNotificationsPrompt) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Rupee also needs permission to post its own alerts (budget warnings, due reminders).",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(onClick = onRequestPostNotifications) {
                    Text("Allow alerts")
                }
            }
        }
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenRecap),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Monthly recap", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("Top categories, merchants, and biggest expenses for the month.", style = MaterialTheme.typography.bodyMedium)
                }
                Text("›", style = MaterialTheme.typography.headlineSmall)
            }
        }
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenRecurring),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Recurring", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("Auto-detected subscriptions and dues to confirm.", style = MaterialTheme.typography.bodyMedium)
                }
                Text("›", style = MaterialTheme.typography.headlineSmall)
            }
        }
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenCardsEmis),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Cards & EMIs", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("Track credit cards, dues, and EMI plans.", style = MaterialTheme.typography.bodyMedium)
                }
                Text("›", style = MaterialTheme.typography.headlineSmall)
            }
        }
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenTrustRules),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Trusted merchants", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = if (state.trustRules.isEmpty()) "No rules yet" else "${state.trustRules.size} rule${if (state.trustRules.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Text("›", style = MaterialTheme.typography.headlineSmall)
            }
        }
        SettingsCard(title = "Categories") {
            if (state.categories.isEmpty()) {
                Text("No categories yet.", style = MaterialTheme.typography.bodyMedium)
            } else {
                state.categories.forEach { name ->
                    Text(name, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        SettingsCard(title = "Buckets") {
            if (state.buckets.isEmpty()) {
                Text("No buckets yet.", style = MaterialTheme.typography.bodyMedium)
            } else {
                state.buckets.forEach { name ->
                    Text(name, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        SettingsCard(title = "Supported notifications") {
            Text(
                "Rupee currently reads notifications from these apps with dedicated parsers:",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(modifier = Modifier.height(8.dp))
            listOf(
                "Google Pay (GPay)",
                "PhonePe",
                "Paytm",
                "CRED",
                "ICICI credit card alerts",
                "Generic UPI fallback (PhonePe-clones, BHIM, etc.)",
                "EMI / loan reminders (heuristic)",
            ).forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Other apps may fall through a generic parser with lower confidence — those land in Inbox for you to confirm or dismiss.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        SettingsCard(title = "Privacy & data") {
            Text(
                "Notification bodies stay on this device only — there is no cloud sync yet. " +
                    "If you uninstall or reset, your data is gone. A cloud backup is on the roadmap.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        SettingsCard(title = "Export data") {
            Text(
                "Save a copy of your ledger and share it. CSV is spreadsheet-friendly. " +
                    "JSON is structured for downstream tools.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onExportLedgerCsv) { Text("Export CSV") }
                OutlinedButton(onClick = onExportLedgerJson) { Text("Export JSON") }
            }
        }
        SettingsCard(title = "Feedback") {
            Text(
                "Found a bug or have a suggestion? Send me an email.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(onClick = onSendFeedback) { Text("Send feedback") }
        }
        SettingsCard(title = "About") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Version", style = MaterialTheme.typography.bodyMedium)
                Text(state.appVersion, style = MaterialTheme.typography.bodyMedium)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Currency", style = MaterialTheme.typography.bodyMedium)
                Text(state.currencyCode, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(onClick = onOpenDebug) { Text("Open debug tools") }
        }
        state.message?.let {
            Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
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
