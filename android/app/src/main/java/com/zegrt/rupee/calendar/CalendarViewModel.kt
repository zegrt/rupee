package com.zegrt.rupee.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.zegrt.rupee.data.local.entity.CanonicalTransactionEntity
import com.zegrt.rupee.data.repository.LocalFinanceRepository
import com.zegrt.rupee.ingestion.MerchantNameUtils
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Currency
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

data class CalendarDayCell(
    val date: LocalDate,
    val inMonth: Boolean,
    val totalLabel: String?,
    val isToday: Boolean,
    val isSelected: Boolean,
)

data class CalendarTxnRow(
    val id: String,
    val merchant: String,
    val subline: String,
    val amountLabel: String,
    val isSuggested: Boolean,
)

data class CalendarUiState(
    val monthLabel: String = "",
    val days: List<CalendarDayCell> = emptyList(),
    val totalLabel: String = "",
    val selectedDate: LocalDate? = null,
    val selectedDayLabel: String? = null,
    val selectedTransactions: List<CalendarTxnRow> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(
    private val repository: LocalFinanceRepository,
    private val clock: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {

    private val today = MutableStateFlow(clock())
    private val displayMonth = MutableStateFlow(YearMonth.from(clock()))
    private val selectedDate = MutableStateFlow<LocalDate?>(null)

    private val moneyFormatter = NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
        maximumFractionDigits = 0
        currency = Currency.getInstance("INR")
    }
    private val rowMoneyFormatter = NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
        maximumFractionDigits = 2
        currency = Currency.getInstance("INR")
    }
    private val dayHeadlineFormatter = DateTimeFormatter.ofPattern("EEEE, d MMM yyyy")
    private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")

    private val transactionsForMonth = displayMonth.flatMapLatest { month ->
        repository.observeTransactionsInPeriod(
            fromIso = month.atDay(1).toString(),
            untilIso = month.plusMonths(1).atDay(1).toString(),
        )
    }

    val uiState: StateFlow<CalendarUiState> = combine(
        displayMonth,
        selectedDate,
        transactionsForMonth,
        today,
    ) { month, selected, txns, now ->
        toUiState(month, selected, txns, now)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CalendarUiState(),
    )

    fun goToPreviousMonth() {
        displayMonth.value = displayMonth.value.minusMonths(1)
        selectedDate.value = null
    }

    fun goToNextMonth() {
        displayMonth.value = displayMonth.value.plusMonths(1)
        selectedDate.value = null
    }

    fun selectDate(date: LocalDate) {
        selectedDate.value = if (selectedDate.value == date) null else date
    }

    fun closeDayDetail() {
        selectedDate.value = null
    }

    private fun toUiState(
        month: YearMonth,
        selected: LocalDate?,
        txns: List<CanonicalTransactionEntity>,
        now: LocalDate,
    ): CalendarUiState {
        val totalsByDate = txns
            .filter { it.type.name == "EXPENSE" }
            .groupBy { occurredLocalDate(it.occurredAt) }
            .mapValues { (_, list) -> list.sumOf { it.amountMinor } }

        val firstOfMonth = month.atDay(1)
        val gridStart = firstOfMonth.with(DayOfWeek.MONDAY).let {
            if (it.isAfter(firstOfMonth)) it.minusWeeks(1) else it
        }
        val gridEnd = gridStart.plusWeeks(6)
        val cells = mutableListOf<CalendarDayCell>()
        var cursor = gridStart
        while (cursor.isBefore(gridEnd)) {
            val total = totalsByDate[cursor]
            cells += CalendarDayCell(
                date = cursor,
                inMonth = YearMonth.from(cursor) == month,
                totalLabel = total?.let { compactRupees(it) },
                isToday = cursor == now,
                isSelected = cursor == selected,
            )
            cursor = cursor.plusDays(1)
        }

        val totalAmount = totalsByDate.values.sum()
        val monthLabel = "${month.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} ${month.year}"
        val selectedTxnRows = if (selected != null) {
            txns.filter { occurredLocalDate(it.occurredAt) == selected }.map(::toRow)
        } else emptyList()
        val selectedHeadline = selected?.format(dayHeadlineFormatter)

        return CalendarUiState(
            monthLabel = monthLabel,
            days = cells,
            totalLabel = "Spent this month: ${moneyFormatter.format(totalAmount / 100.0)}",
            selectedDate = selected,
            selectedDayLabel = selectedHeadline,
            selectedTransactions = selectedTxnRows,
        )
    }

    private fun occurredLocalDate(iso: String): LocalDate = try {
        Instant.parse(iso).atZone(ZoneId.systemDefault()).toLocalDate()
    } catch (_: Exception) {
        LocalDate.parse(iso.take(10))
    }

    private fun compactRupees(minor: Long): String {
        val rupees = minor / 100
        return when {
            rupees >= 100_000 -> "₹${"%.1f".format(rupees / 100_000.0)}L"
            rupees >= 1_000 -> "₹${"%.1f".format(rupees / 1_000.0)}k"
            else -> "₹$rupees"
        }
    }

    private fun toRow(txn: CanonicalTransactionEntity): CalendarTxnRow {
        val merchant = MerchantNameUtils.clean(txn.merchantName)
        val time = runCatching {
            Instant.parse(txn.occurredAt).atZone(ZoneId.systemDefault()).format(timeFormatter)
        }.getOrNull()
        val subline = listOfNotNull(
            txn.mode?.name?.replace('_', ' ')?.lowercase()?.replaceFirstChar { it.uppercase() },
            time,
        ).joinToString(" • ")
        return CalendarTxnRow(
            id = txn.id,
            merchant = merchant,
            subline = subline,
            amountLabel = rowMoneyFormatter.format(txn.amountMinor / 100.0),
            isSuggested = txn.status.name == "SUGGESTED",
        )
    }
}

class CalendarViewModelFactory(
    private val repository: LocalFinanceRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(CalendarViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }
        return CalendarViewModel(repository) as T
    }
}
