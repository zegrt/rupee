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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Settings → Accounts. Lists every account and exposes the two
 * "exclude from totals" toggles (expense + income). The columns have
 * existed on AccountEntity since v0.13.x; this screen is the first
 * user-facing surface that flips them — mirroring the Cards & EMIs
 * screen's `excludeFromExpenseTotals` toggle which already shipped for
 * credit cards.
 *
 * Use case: users with a savings account that auto-debits loan EMIs
 * want to exclude that account from spend totals without hiding each
 * transaction individually. Same shape on the income side for accounts
 * that mostly receive transfers between own accounts (which the parser
 * sometimes classifies as INCOME).
 */
@Composable
fun AccountsScreen(
    accounts: List<AccountSettingsRow>,
    onToggleExcludeExpense: (String, Boolean) -> Unit,
    onToggleExcludeIncome: (String, Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            "Choose which accounts contribute to your monthly spend and income totals. Hidden accounts still capture transactions — they just don't roll up.",
            style = MaterialTheme.typography.bodyMedium,
        )
        if (accounts.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        "No accounts yet.",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Add an account during onboarding to manage exclusion here.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        } else {
            accounts.forEach { account ->
                AccountCard(
                    row = account,
                    onToggleExcludeExpense = { exclude -> onToggleExcludeExpense(account.id, exclude) },
                    onToggleExcludeIncome = { exclude -> onToggleExcludeIncome(account.id, exclude) },
                )
            }
        }
    }
}

@Composable
private fun AccountCard(
    row: AccountSettingsRow,
    onToggleExcludeExpense: (Boolean) -> Unit,
    onToggleExcludeIncome: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                row.displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            val subtitle = listOfNotNull(row.providerName?.takeIf { it.isNotBlank() }, row.typeLabel)
                .joinToString(" · ")
            if (subtitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            ToggleRow(
                title = "Hide from expense totals",
                subtitle = "Outflows from this account don't count toward monthly spend.",
                checked = row.excludeFromExpenseTotals,
                onToggle = onToggleExcludeExpense,
            )
            Spacer(modifier = Modifier.height(8.dp))
            ToggleRow(
                title = "Hide from income totals",
                subtitle = "Inflows to this account don't count toward monthly income.",
                checked = row.excludeFromIncomeTotals,
                onToggle = onToggleExcludeIncome,
            )
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}
