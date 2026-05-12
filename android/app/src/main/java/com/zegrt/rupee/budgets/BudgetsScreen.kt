package com.zegrt.rupee.budgets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
fun BudgetsScreen(
    state: BudgetsUiState,
    onMonthlyDraftChange: (String) -> Unit,
    onSaveMonthly: () -> Unit,
    onCategoryDraftChange: (String, String) -> Unit,
    onSaveCategory: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(state.monthLabel, style = MaterialTheme.typography.bodyMedium)

        MonthlyHero(
            monthly = state.monthly,
            onDraftChange = onMonthlyDraftChange,
            onSave = onSaveMonthly,
        )

        Text("Per category", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "Spend over a category limit shows in red. Enter 0 to clear a limit.",
            style = MaterialTheme.typography.bodySmall,
        )

        state.categoryRows.forEach { row ->
            CategoryBudgetCard(
                row = row,
                onDraftChange = { v -> onCategoryDraftChange(row.categoryId, v) },
                onSave = { onSaveCategory(row.categoryId) },
            )
        }

        Text("Custom buckets", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Bucket-level budgets are on the roadmap.", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Today you can set buckets in Settings → Buckets, but spend isn't aggregated to them yet. " +
                        "We need transaction-to-bucket tagging first.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        state.message?.let {
            Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun MonthlyHero(
    monthly: MonthlyBudgetState,
    onDraftChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    val accent = when {
        monthly.isOverLimit -> MaterialTheme.colorScheme.error
        monthly.isNearLimit -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Monthly budget", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(monthly.limitLabel, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                listOfNotNull(
                    monthly.spentLabel,
                    monthly.remainingLabel.takeIf { it.isNotBlank() },
                ).joinToString(" • "),
                style = MaterialTheme.typography.bodyMedium,
                color = if (monthly.isOverLimit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (monthly.hasLimit) {
                LinearProgressIndicator(
                    progress = { monthly.progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = accent,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = monthly.draftRupees,
                    onValueChange = onDraftChange,
                    label = { Text("Limit (₹)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(4.dp))
                TextButton(
                    onClick = onSave,
                    enabled = !monthly.isSaving && monthly.draftRupees.isNotBlank() && monthly.draftRupees != monthly.limitRupees,
                ) {
                    Text(if (monthly.isSaving) "..." else "Save")
                }
            }
        }
    }
}

@Composable
private fun CategoryBudgetCard(
    row: CategoryBudgetRow,
    onDraftChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column {
                Text(row.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    text = listOfNotNull(row.spentLabel, row.limitLabel).joinToString(" "),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (row.isOverLimit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (row.hasLimit) {
                LinearProgressIndicator(
                    progress = { row.progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = if (row.isOverLimit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = row.draftRupees,
                    onValueChange = onDraftChange,
                    label = { Text("Limit (₹)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(4.dp))
                TextButton(
                    onClick = onSave,
                    enabled = !row.isSaving && row.draftRupees != row.limitRupees,
                ) {
                    Text(if (row.isSaving) "..." else "Save")
                }
            }
        }
    }
}
