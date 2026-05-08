package com.zegrt.rupee.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun CardsEmisScreen(
    state: CardsEmisUiState,
    onAddEmi: () -> Unit,
    onRemoveEmi: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("Credit cards", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        if (state.cards.isEmpty()) {
            EmptyCard("No cards yet. Cards added during onboarding show up here.")
        } else {
            state.cards.forEach { CardRowCard(it) }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("EMI plans", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            TextButton(onClick = onAddEmi) { Text("Add") }
        }
        if (state.emis.isEmpty()) {
            EmptyCard("No EMIs tracked yet. Add one manually for now — auto-detection from notifications is coming.")
        } else {
            state.emis.forEach { EmiRowCard(it, onRemove = { onRemoveEmi(it.id) }) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmiDraftSheet(
    draft: EmiDraft,
    onClose: () -> Unit,
    onUpdate: (EmiDraft.() -> EmiDraft) -> Unit,
    onSubmit: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Add EMI plan", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = draft.name,
                onValueChange = { v -> onUpdate { copy(name = v, error = null) } },
                label = { Text("Plan name (e.g. iPhone 15)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = draft.monthlyRupees,
                onValueChange = { v -> onUpdate { copy(monthlyRupees = v.filter { it.isDigit() }, error = null) } },
                label = { Text("Monthly amount (₹)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            OutlinedTextField(
                value = draft.tenureMonths,
                onValueChange = { v -> onUpdate { copy(tenureMonths = v.filter { it.isDigit() }, error = null) } },
                label = { Text("Months remaining (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            OutlinedTextField(
                value = draft.nextDueDate,
                onValueChange = { v -> onUpdate { copy(nextDueDate = v, error = null) } },
                label = { Text("Next due date — yyyy-MM-dd (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = draft.notes,
                onValueChange = { v -> onUpdate { copy(notes = v, error = null) } },
                label = { Text("Notes (optional)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
            draft.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onSubmit, enabled = !draft.isSaving, modifier = Modifier.weight(1f)) {
                    Text(if (draft.isSaving) "Saving..." else "Save")
                }
                OutlinedButton(onClick = onClose, modifier = Modifier.weight(1f)) { Text("Cancel") }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun CardRowCard(row: CardRow) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(row.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(row.subtitle, style = MaterialTheme.typography.bodySmall)
            row.outstandingLabel?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            row.limitLabel?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            row.dueLabel?.let { Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun EmiRowCard(row: EmiRow, onRemove: () -> Unit) {
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
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(row.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(row.monthlyLabel, style = MaterialTheme.typography.bodyMedium)
                row.tenureLabel?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                row.nextDueLabel?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                row.outstandingLabel?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
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
