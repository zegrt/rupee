package com.zegrt.rupee.settings

import androidx.compose.foundation.BorderStroke
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
    onBudgetDraftChange: (String) -> Unit,
    onSaveBudget: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenDebug: () -> Unit,
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
        SettingsCard(title = "Monthly budget") {
            Text(
                text = "Current: ${state.monthlyBudgetLabel}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = state.monthlyBudgetDraft,
                onValueChange = onBudgetDraftChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Limit (₹)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onSaveBudget,
                enabled = !state.savingBudget && state.monthlyBudgetDraft.isNotBlank() && state.monthlyBudgetDraft != state.monthlyBudgetRupees,
            ) {
                Text(if (state.savingBudget) "Saving..." else "Save budget")
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
