package com.zegrt.rupee.recurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.zegrt.rupee.data.local.entity.RecurringPatternEntity
import com.zegrt.rupee.data.repository.LocalFinanceRepository
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RecurringRow(
    val id: String,
    val merchantPattern: String,
    val amountLabel: String,
    val cadenceLabel: String,
    val nextDueLabel: String,
    val occurrenceLabel: String,
    val isConfirmed: Boolean,
)

data class RecurringUiState(
    val suggestions: List<RecurringRow> = emptyList(),
    val confirmed: List<RecurringRow> = emptyList(),
    val isRefreshing: Boolean = false,
)

class RecurringViewModel(
    private val repository: LocalFinanceRepository,
) : ViewModel() {

    private val moneyFormatter = NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
        maximumFractionDigits = 0
        currency = Currency.getInstance("INR")
    }
    private val dueFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")

    val uiState: StateFlow<RecurringUiState> = repository.observeRecurringPatterns()
        .map { patterns ->
            val rows = patterns.map(::toRow)
            RecurringUiState(
                suggestions = rows.filter { !it.isConfirmed },
                confirmed = rows.filter { it.isConfirmed },
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = RecurringUiState(),
        )

    fun confirm(id: String) {
        viewModelScope.launch { repository.confirmRecurringPattern(id) }
    }

    fun dismiss(id: String) {
        viewModelScope.launch { repository.dismissRecurringPattern(id) }
    }

    fun remove(id: String) {
        viewModelScope.launch { repository.removeRecurringPattern(id) }
    }

    fun refreshNow() {
        viewModelScope.launch { repository.refreshRecurringPatterns() }
    }

    private fun toRow(pattern: RecurringPatternEntity): RecurringRow {
        val cadence = when {
            pattern.intervalDays in 28..31 -> "Monthly"
            pattern.intervalDays in 13..16 -> "Fortnightly"
            pattern.intervalDays in 6..8 -> "Weekly"
            else -> "Every ${pattern.intervalDays} days"
        }
        val nextDue = runCatching { LocalDate.parse(pattern.nextExpectedAt).format(dueFormatter) }
            .getOrDefault(pattern.nextExpectedAt)
        return RecurringRow(
            id = pattern.id,
            merchantPattern = pattern.merchantPattern,
            amountLabel = moneyFormatter.format(pattern.expectedAmountMinor / 100.0),
            cadenceLabel = cadence,
            nextDueLabel = "Next: $nextDue",
            occurrenceLabel = "${pattern.occurrenceCount} payments seen",
            isConfirmed = pattern.isConfirmed,
        )
    }
}

class RecurringViewModelFactory(
    private val repository: LocalFinanceRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(RecurringViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }
        return RecurringViewModel(repository) as T
    }
}
