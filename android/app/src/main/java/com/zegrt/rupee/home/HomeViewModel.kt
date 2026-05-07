package com.zegrt.rupee.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.zegrt.rupee.data.local.entity.BudgetEntity
import com.zegrt.rupee.data.local.entity.CanonicalTransactionEntity
import com.zegrt.rupee.data.local.entity.CanonicalTransactionStatus
import com.zegrt.rupee.data.local.entity.InboxItemEntity
import com.zegrt.rupee.data.local.entity.TransactionCandidateEntity
import com.zegrt.rupee.data.local.entity.UserEntity
import com.zegrt.rupee.data.repository.LocalFinanceRepository
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class HomeTab {
    HOME,
    INBOX,
    TRANSACTIONS,
}

data class HomeInboxRow(
    val id: String,
    val headline: String,
    val subline: String,
    val reasonLabel: String,
    val merchantDraft: String,
)

data class HomeTransactionRow(
    val id: String,
    val headline: String,
    val subline: String,
    val amountLabel: String,
    val notes: String,
    val merchantDraft: String,
    val notesDraft: String,
)

data class HomeRecentRow(
    val id: String,
    val merchant: String,
    val subline: String,
    val amountLabel: String,
    val isSuggested: Boolean,
)

data class HomeDashboard(
    val greeting: String = "Hello",
    val monthLabel: String = "",
    val hasMonthlyBudget: Boolean = false,
    val monthlyBudgetLabel: String = "",
    val monthlySpentLabel: String = "",
    val monthlyRemainingLabel: String = "",
    val monthlyProgress: Float = 0f,
    val isOverBudget: Boolean = false,
    val isNearLimit: Boolean = false,
    val weeklySpentLabel: String = "",
    val weekRangeLabel: String = "",
    val pendingInboxCount: Int = 0,
    val recentTransactions: List<HomeRecentRow> = emptyList(),
    val hasAnyTransactions: Boolean = false,
)

data class HomeUiState(
    val userName: String = "Rupee",
    val pendingInboxCount: Int = 0,
    val selectedTab: HomeTab = HomeTab.HOME,
    val selectedInboxItemId: String? = null,
    val selectedTransactionId: String? = null,
    val inboxItems: List<HomeInboxRow> = emptyList(),
    val recentTransactions: List<HomeTransactionRow> = emptyList(),
    val dashboard: HomeDashboard = HomeDashboard(),
    val isSeeding: Boolean = true,
)

private data class DashboardData(
    val user: UserEntity?,
    val transactions: List<CanonicalTransactionEntity>,
    val candidates: List<TransactionCandidateEntity>,
    val inboxItems: List<InboxItemEntity>,
    val budget: BudgetEntity?,
    val monthlySpent: Long,
    val weeklySpent: Long,
)

private data class ViewSelection(
    val isSeeding: Boolean,
    val tab: HomeTab,
    val selectedInboxItemId: String?,
    val selectedTransactionId: String?,
    val inboxMerchantDrafts: Map<String, String>,
    val transactionMerchantDrafts: Map<String, String>,
    val transactionNotesDrafts: Map<String, String>,
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val repository: LocalFinanceRepository,
    private val clock: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {
    private val headlineCurrencyFormatter = currencyFormatter(decimals = 0)
    private val rowCurrencyFormatter = currencyFormatter(decimals = 2)

    private val today = MutableStateFlow(clock())
    private val isSeeding = MutableStateFlow(true)
    private val selectedTab = MutableStateFlow(HomeTab.HOME)
    private val selectedInboxItemId = MutableStateFlow<String?>(null)
    private val selectedTransactionId = MutableStateFlow<String?>(null)
    private val inboxMerchantDrafts = MutableStateFlow<Map<String, String>>(emptyMap())
    private val transactionMerchantDrafts = MutableStateFlow<Map<String, String>>(emptyMap())
    private val transactionNotesDrafts = MutableStateFlow<Map<String, String>>(emptyMap())

    private val monthlyBudgetFlow: Flow<BudgetEntity?> = today.flatMapLatest { date ->
        repository.observeMonthlyTotalBudget(date)
    }
    private val monthlySpendFlow: Flow<Long> = today.flatMapLatest { date ->
        val month = YearMonth.from(date)
        repository.observeSpentInPeriod(
            fromIso = month.atDay(1).toString(),
            untilIso = month.plusMonths(1).atDay(1).toString(),
        )
    }
    private val weeklySpendFlow: Flow<Long> = today.flatMapLatest { date ->
        val weekStart = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        repository.observeSpentInPeriod(
            fromIso = weekStart.toString(),
            untilIso = weekStart.plusDays(7).toString(),
        )
    }

    private val dashboardData: Flow<DashboardData> = combine(
        combine(
            repository.observeUser(),
            repository.observeRecentTransactions(),
            repository.observeRecentTransactionCandidates(),
            repository.observePendingInboxItems(),
        ) { user, txns, cands, inbox -> arrayOf<Any?>(user, txns, cands, inbox) },
        combine(
            monthlyBudgetFlow,
            monthlySpendFlow,
            weeklySpendFlow,
        ) { b, m, w -> Triple(b, m, w) },
    ) { entities, periods ->
        @Suppress("UNCHECKED_CAST")
        DashboardData(
            user = entities[0] as UserEntity?,
            transactions = entities[1] as List<CanonicalTransactionEntity>,
            candidates = entities[2] as List<TransactionCandidateEntity>,
            inboxItems = entities[3] as List<InboxItemEntity>,
            budget = periods.first,
            monthlySpent = periods.second,
            weeklySpent = periods.third,
        )
    }

    private val viewSelection: Flow<ViewSelection> = combine(
        combine(
            isSeeding,
            selectedTab,
            selectedInboxItemId,
            selectedTransactionId,
        ) { seeding, tab, iid, tid -> arrayOf<Any?>(seeding, tab, iid, tid) },
        combine(
            inboxMerchantDrafts,
            transactionMerchantDrafts,
            transactionNotesDrafts,
        ) { im, tm, tn -> Triple(im, tm, tn) },
    ) { selection, drafts ->
        ViewSelection(
            isSeeding = selection[0] as Boolean,
            tab = selection[1] as HomeTab,
            selectedInboxItemId = selection[2] as String?,
            selectedTransactionId = selection[3] as String?,
            inboxMerchantDrafts = drafts.first,
            transactionMerchantDrafts = drafts.second,
            transactionNotesDrafts = drafts.third,
        )
    }

    val uiState: StateFlow<HomeUiState> = combine(dashboardData, viewSelection) { data, selection ->
        toUiState(data, selection, today.value)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    init {
        viewModelScope.launch {
            repository.ensureBaseData()
            repository.ensureMonthlyBudgetForToday(clock())
            isSeeding.value = false
        }
    }

    fun refreshOnResume() {
        val now = clock()
        if (today.value != now) today.value = now
        viewModelScope.launch {
            repository.ensureMonthlyBudgetForToday(now)
        }
    }

    fun selectTab(tab: HomeTab) {
        selectedTab.value = tab
    }

    fun selectInboxItem(id: String) {
        selectedInboxItemId.value = id
    }

    fun updateInboxMerchantDraft(id: String, value: String) {
        inboxMerchantDrafts.value = inboxMerchantDrafts.value + (id to value)
    }

    fun confirmInboxItem(id: String) {
        viewModelScope.launch {
            repository.confirmInboxItem(
                inboxItemId = id,
                merchantNameOverride = inboxMerchantDrafts.value[id],
            )
            inboxMerchantDrafts.value = inboxMerchantDrafts.value - id
            selectedInboxItemId.value = null
        }
    }

    fun dismissInboxItem(id: String) {
        viewModelScope.launch {
            repository.dismissInboxItem(id)
            inboxMerchantDrafts.value = inboxMerchantDrafts.value - id
            selectedInboxItemId.value = null
        }
    }

    fun selectTransaction(id: String) {
        selectedTransactionId.value = id
    }

    fun updateTransactionMerchantDraft(id: String, value: String) {
        transactionMerchantDrafts.value = transactionMerchantDrafts.value + (id to value)
    }

    fun updateTransactionNotesDraft(id: String, value: String) {
        transactionNotesDrafts.value = transactionNotesDrafts.value + (id to value)
    }

    fun saveTransactionEdits(id: String) {
        viewModelScope.launch {
            repository.updateTransactionDetails(
                transactionId = id,
                merchantName = transactionMerchantDrafts.value[id].orEmpty(),
                notes = transactionNotesDrafts.value[id].orEmpty(),
            )
        }
    }

    private fun toUiState(
        data: DashboardData,
        selection: ViewSelection,
        date: LocalDate,
    ): HomeUiState {
        val candidatesById = data.candidates.associateBy { it.id }
        val inboxRows = data.inboxItems.map { inboxItem ->
            val candidate = candidatesById[inboxItem.transactionCandidateId]
            HomeInboxRow(
                id = inboxItem.id,
                headline = candidate?.toEntityName ?: "Review transaction",
                subline = listOfNotNull(
                    candidate?.mode?.name?.replace('_', ' '),
                    candidate?.amountMinor?.let(::formatRowAmount),
                    candidate?.occurredAt,
                ).joinToString(" • ").ifBlank { inboxItem.createdAt },
                reasonLabel = inboxItem.reasonCode.name.replace('_', ' '),
                merchantDraft = selection.inboxMerchantDrafts[inboxItem.id]
                    ?: candidate?.toEntityName.orEmpty(),
            )
        }
        val transactionRows = data.transactions.take(20).map { transaction ->
            HomeTransactionRow(
                id = transaction.id,
                headline = transaction.merchantName ?: "Unnamed transaction",
                subline = listOfNotNull(
                    transaction.mode?.name?.replace('_', ' '),
                    transaction.sourceSummary,
                    transaction.occurredAt,
                ).joinToString(" • ").ifBlank { transaction.occurredAt },
                amountLabel = formatRowAmount(transaction.amountMinor),
                notes = transaction.notes.orEmpty(),
                merchantDraft = selection.transactionMerchantDrafts[transaction.id]
                    ?: transaction.merchantName.orEmpty(),
                notesDraft = selection.transactionNotesDrafts[transaction.id]
                    ?: transaction.notes.orEmpty(),
            )
        }

        return HomeUiState(
            userName = data.user?.displayName ?: "Rupee",
            pendingInboxCount = data.inboxItems.size,
            selectedTab = selection.tab,
            selectedInboxItemId = selection.selectedInboxItemId ?: inboxRows.firstOrNull()?.id,
            selectedTransactionId = selection.selectedTransactionId ?: transactionRows.firstOrNull()?.id,
            inboxItems = inboxRows,
            recentTransactions = transactionRows,
            dashboard = buildDashboard(data, date),
            isSeeding = selection.isSeeding,
        )
    }

    private fun buildDashboard(data: DashboardData, date: LocalDate): HomeDashboard {
        val month = YearMonth.from(date)
        val weekStart = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val weekEnd = weekStart.plusDays(6)
        val limit = data.budget?.limitMinor ?: 0L
        val remaining = (limit - data.monthlySpent).coerceAtLeast(0L)
        val progress = if (limit > 0L) {
            (data.monthlySpent.toDouble() / limit.toDouble()).toFloat().coerceIn(0f, 1f)
        } else 0f
        val threshold = data.budget?.alertThresholdPercent?.toFloat() ?: 0.8f
        val isOver = limit > 0L && data.monthlySpent > limit
        val isNear = !isOver && limit > 0L && progress >= threshold

        val recents = data.transactions.take(5).map { txn ->
            HomeRecentRow(
                id = txn.id,
                merchant = txn.merchantName ?: "Unnamed",
                subline = listOfNotNull(
                    txn.mode?.name?.replace('_', ' ')?.lowercase()?.replaceFirstChar { it.uppercase() },
                    txn.sourceSummary,
                ).joinToString(" • ").ifBlank { txn.occurredAt.take(10) },
                amountLabel = formatRowAmount(txn.amountMinor),
                isSuggested = txn.status == CanonicalTransactionStatus.SUGGESTED,
            )
        }

        return HomeDashboard(
            greeting = greetingFor(data.user?.displayName),
            monthLabel = "${month.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} ${month.year}",
            hasMonthlyBudget = data.budget != null,
            monthlyBudgetLabel = if (data.budget != null) formatHeadlineAmount(limit) else "Set a monthly budget",
            monthlySpentLabel = formatHeadlineAmount(data.monthlySpent),
            monthlyRemainingLabel = formatHeadlineAmount(remaining),
            monthlyProgress = progress,
            isOverBudget = isOver,
            isNearLimit = isNear,
            weeklySpentLabel = formatHeadlineAmount(data.weeklySpent),
            weekRangeLabel = formatWeekRange(weekStart, weekEnd),
            pendingInboxCount = data.inboxItems.size,
            recentTransactions = recents,
            hasAnyTransactions = data.transactions.isNotEmpty(),
        )
    }

    private fun greetingFor(name: String?): String {
        val timeOfDay = when (LocalTime.now().hour) {
            in 0..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            else -> "Good evening"
        }
        return if (!name.isNullOrBlank()) "$timeOfDay, $name" else timeOfDay
    }

    private fun formatHeadlineAmount(value: Long): String =
        headlineCurrencyFormatter.format(value / 100.0)

    private fun formatRowAmount(value: Long): String =
        rowCurrencyFormatter.format(value / 100.0)

    private fun formatWeekRange(start: LocalDate, end: LocalDate): String {
        val startMonth = start.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
        val endMonth = end.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
        return if (start.month == end.month) {
            "${start.dayOfMonth} – ${end.dayOfMonth} $endMonth"
        } else {
            "${start.dayOfMonth} $startMonth – ${end.dayOfMonth} $endMonth"
        }
    }

    private fun currencyFormatter(decimals: Int): NumberFormat =
        NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
            minimumFractionDigits = decimals
            maximumFractionDigits = decimals
            currency = java.util.Currency.getInstance("INR")
        }
}

class HomeViewModelFactory(
    private val repository: LocalFinanceRepository,
    private val clock: () -> LocalDate = { LocalDate.now() },
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }
        return HomeViewModel(repository, clock) as T
    }
}
