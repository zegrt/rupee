package com.zegrt.rupee.recurring

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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun RecurringScreen(
    state: RecurringUiState,
    onConfirm: (String) -> Unit,
    onDismiss: (String) -> Unit,
    onRemove: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "Auto-detected from the last 90 days of confirmed transactions.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRefresh) { Text("Refresh") }
        }

        if (state.suggestions.isNotEmpty()) {
            Text("Suggested", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            state.suggestions.forEach { row ->
                SuggestionCard(
                    row = row,
                    onConfirm = { onConfirm(row.id) },
                    onDismiss = { onDismiss(row.id) },
                )
            }
        }

        Text("Confirmed", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (state.confirmed.isEmpty()) {
            EmptyCard("Confirm a suggestion above to start tracking it here. Confirmed recurring charges will surface on Home as upcoming dues (coming soon).")
        } else {
            state.confirmed.forEach { row ->
                ConfirmedCard(row = row, onRemove = { onRemove(row.id) })
            }
        }

        if (state.suggestions.isEmpty() && state.confirmed.isEmpty()) {
            EmptyCard("No recurring spend detected yet. Once you have 3+ similar charges from the same merchant within 20–35 day intervals, suggestions will appear here.")
        }
    }
}

@Composable
private fun SuggestionCard(
    row: RecurringRow,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(row.merchantPattern, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("${row.cadenceLabel} • ${row.occurrenceLabel}", style = MaterialTheme.typography.bodySmall)
                    Text(row.nextDueLabel, style = MaterialTheme.typography.bodySmall)
                }
                Text(row.amountLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onConfirm, modifier = Modifier.weight(1f)) { Text("Confirm") }
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Not recurring") }
            }
        }
    }
}

@Composable
private fun ConfirmedCard(row: RecurringRow, onRemove: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(row.merchantPattern, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(row.amountLabel + " • " + row.cadenceLabel, style = MaterialTheme.typography.bodyMedium)
                Text(row.nextDueLabel, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onRemove) { Text("Remove") }
        }
    }
}

@Composable
private fun EmptyCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Text(message, modifier = Modifier.padding(20.dp), style = MaterialTheme.typography.bodyMedium)
    }
}
