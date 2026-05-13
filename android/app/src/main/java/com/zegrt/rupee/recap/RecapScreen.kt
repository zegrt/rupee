package com.zegrt.rupee.recap

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun RecapScreen(
    state: RecapUiState,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = onPrev) { Text("‹") }
            Text(state.monthLabel, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            TextButton(onClick = onNext) { Text("›") }
        }

        HeroCard(state)

        if (!state.hasData) {
            EmptyCard("No transactions in this month yet.")
            return@Column
        }

        Section(title = "Top categories", items = state.topCategories)
        Section(title = "Top merchants", items = state.topMerchants)
        Section(title = "Biggest transactions", items = state.biggestTransactions)
    }
}

@Composable
private fun HeroCard(state: RecapUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Total spent", style = MaterialTheme.typography.bodyMedium)
            Text(state.totalLabel, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(state.transactionCountLabel, style = MaterialTheme.typography.bodySmall)
            if (state.avgPerDayLabel.isNotBlank()) {
                Text(state.avgPerDayLabel, style = MaterialTheme.typography.bodySmall)
            }
            state.deltaVsLastMonth?.let { delta ->
                Text(
                    delta.text,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (delta.isUp) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
            state.totalReceivedLabel?.let { received ->
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 6.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                Text("Received", style = MaterialTheme.typography.bodyMedium)
                Text(
                    received,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                state.netLabel?.let { net ->
                    Text(net, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, items: List<RecapHighlight>) {
    if (items.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                items.forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            item.countLabel?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Text(item.amountLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
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
