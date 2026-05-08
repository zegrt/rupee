package com.zegrt.rupee.recap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.zegrt.rupee.data.local.entity.CanonicalTransactionEntity
import com.zegrt.rupee.data.local.entity.CanonicalTransactionStatus
import com.zegrt.rupee.data.local.entity.CanonicalTransactionType
import com.zegrt.rupee.data.local.entity.CategoryEntity
import com.zegrt.rupee.data.repository.LocalFinanceRepository
import com.zegrt.rupee.ingestion.MerchantNameUtils
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
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

data class RecapHighlight(
    val name: String,
    val amountLabel: String,
    val countLabel: String?,
)

data class RecapDeltaLabel(
    val text: String,
    val isUp: Boolean,
)

data class RecapUiState(
    val monthLabel: String = "",
    val totalLabel: String = "—",
    val transactionCountLabel: String = "0 transactions",
    val avgPerDayLabel: String = "",
    val deltaVsLastMonth: RecapDeltaLabel? = null,
    val topCategories: List<RecapHighlight> = emptyList(),
    val topMerchants: List<RecapHighlight> = emptyList(),
    val biggestTransactions: List<RecapHighlight> = emptyList(),
    val hasData: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class RecapViewModel(
    private val repository: LocalFinanceRepository,
    private val clock: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {

    private val month = MutableStateFlow(YearMonth.from(clock()))

    private val moneyFormatter = NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
        maximumFractionDigits = 0
        currency = Currency.getInstance("INR")
    }

    private val currentMonthTxns = month.flatMapLatest { ym ->
        repository.observeTransactionsInPeriod(
            fromIso = ym.atDay(1).toString(),
            untilIso = ym.plusMonths(1).atDay(1).toString(),
        )
    }
    private val previousMonthTxns = month.flatMapLatest { ym ->
        val prev = ym.minusMonths(1)
        repository.observeTransactionsInPeriod(
            fromIso = prev.atDay(1).toString(),
            untilIso = prev.plusMonths(1).atDay(1).toString(),
        )
    }

    val uiState: StateFlow<RecapUiState> = combine(
        month,
        currentMonthTxns,
        previousMonthTxns,
        repository.observeCategories(),
    ) { ym, current, previous, cats ->
        toUiState(ym, current, previous, cats)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RecapUiState(),
    )

    fun goToPreviousMonth() {
        month.value = month.value.minusMonths(1)
    }

    fun goToNextMonth() {
        month.value = month.value.plusMonths(1)
    }

    fun resetToCurrent() {
        month.value = YearMonth.from(clock())
    }

    private fun toUiState(
        ym: YearMonth,
        current: List<CanonicalTransactionEntity>,
        previous: List<CanonicalTransactionEntity>,
        cats: List<CategoryEntity>,
    ): RecapUiState {
        val expenses = current.filter {
            it.type == CanonicalTransactionType.EXPENSE &&
                it.status != CanonicalTransactionStatus.IGNORED &&
                !it.isHiddenFromBudget
        }
        val totalMinor = expenses.sumOf { it.amountMinor }
        val prevTotalMinor = previous.filter {
            it.type == CanonicalTransactionType.EXPENSE &&
                it.status != CanonicalTransactionStatus.IGNORED &&
                !it.isHiddenFromBudget
        }.sumOf { it.amountMinor }

        val daysCounted = if (ym == YearMonth.from(clock())) clock().dayOfMonth else ym.lengthOfMonth()
        val avgPerDay = if (daysCounted > 0) totalMinor / daysCounted else 0L

        val catNameById = cats.associate { it.id to it.name }
        val topCategories = expenses
            .filter { it.categoryId != null }
            .groupBy { it.categoryId!! }
            .map { (catId, list) ->
                RecapHighlight(
                    name = catNameById[catId] ?: "Uncategorized",
                    amountLabel = moneyFormatter.format(list.sumOf { it.amountMinor } / 100.0),
                    countLabel = "${list.size} txn${if (list.size == 1) "" else "s"}",
                )
            }
            .sortedByDescending { parseAmount(it.amountLabel) }
            .take(5)

        val topMerchants = expenses
            .groupBy { MerchantNameUtils.clean(it.merchantName) }
            .map { (merchant, list) ->
                RecapHighlight(
                    name = merchant,
                    amountLabel = moneyFormatter.format(list.sumOf { it.amountMinor } / 100.0),
                    countLabel = "${list.size} txn${if (list.size == 1) "" else "s"}",
                )
            }
            .sortedByDescending { parseAmount(it.amountLabel) }
            .take(5)

        val biggest = expenses
            .sortedByDescending { it.amountMinor }
            .take(5)
            .map { txn ->
                RecapHighlight(
                    name = MerchantNameUtils.clean(txn.merchantName),
                    amountLabel = moneyFormatter.format(txn.amountMinor / 100.0),
                    countLabel = txn.occurredAt.take(10),
                )
            }

        val delta = if (prevTotalMinor > 0L) {
            val diff = totalMinor - prevTotalMinor
            val pct = (diff.toDouble() / prevTotalMinor.toDouble()) * 100.0
            val arrow = if (diff >= 0L) "↑" else "↓"
            RecapDeltaLabel(
                text = "$arrow ${"%.0f".format(kotlin.math.abs(pct))}% vs last month",
                isUp = diff >= 0L,
            )
        } else null

        return RecapUiState(
            monthLabel = "${ym.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} ${ym.year}",
            totalLabel = moneyFormatter.format(totalMinor / 100.0),
            transactionCountLabel = "${expenses.size} transaction${if (expenses.size == 1) "" else "s"}",
            avgPerDayLabel = if (daysCounted > 0) "${moneyFormatter.format(avgPerDay / 100.0)} per day" else "",
            deltaVsLastMonth = delta,
            topCategories = topCategories,
            topMerchants = topMerchants,
            biggestTransactions = biggest,
            hasData = expenses.isNotEmpty(),
        )
    }

    private fun parseAmount(label: String): Long {
        // For sorting only; pull digits and parse.
        val digits = label.filter { it.isDigit() }
        return digits.toLongOrNull() ?: 0L
    }
}

class RecapViewModelFactory(
    private val repository: LocalFinanceRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(RecapViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }
        return RecapViewModel(repository) as T
    }
}
