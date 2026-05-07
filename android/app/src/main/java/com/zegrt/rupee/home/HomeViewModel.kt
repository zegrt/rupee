package com.zegrt.rupee.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.zegrt.rupee.data.local.entity.BudgetEntity
import com.zegrt.rupee.data.local.entity.CanonicalTransactionEntity
import com.zegrt.rupee.data.local.entity.CanonicalTransactionStatus
import com.zegrt.rupee.data.local.entity.CategoryEntity
import com.zegrt.rupee.data.local.entity.InboxItemEntity
import com.zegrt.rupee.data.local.entity.Mode
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
    SETTINGS,
    DEBUG,
}

enum class ReviewSource { INBOX, SUGGESTED }

data class HomeReviewRow(
    val id: String,
    val source: ReviewSource,
    val merchant: String,
    val merchantDraft: String,
    val amountLabel: String,
    val amountDraftRupees: String,
    val categoryId: String?,
    val categoryIdDraft: String?,
    val subline: String,
    val reasonLabel: String,
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

data class CategoryOption(
    val id: String,
    val name: String,
)

data class ManualEntryDraft(
    val isOpen: Boolean = false,
    val merchant: String = "",
    val amountRupees: String = "",
    val mode: Mode = Mode.UPI,
    val categoryId: String? = null,
    val notes: String = "",
    val isSaving: Boolean = false,
    val error: String? = null,
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
    val pendingReviewCount: Int = 0,
    val recentTransactions: List<HomeRecentRow> = emptyList(),
    val hasAnyTransactions: Boolean = false,
)

data class HomeUiState(
    val userName: String = "Rupee",
    val pendingReviewCount: Int = 0,
    val selectedTab: HomeTab = HomeTab.HOME,
    val selectedReviewRowId: String? = null,
    val selectedTransactionId: String? = null,
    val reviewRows: List<HomeReviewRow> = emptyList(),
    val recentTransactions: List<HomeTransactionRow> = emptyList(),
    val categories: List<CategoryOption> = emptyList(),
    val manualEntry: ManualEntryDraft = ManualEntryDraft(),
    val dashboard: HomeDashboard = HomeDashboard(),
    val isSeeding: Boolean = true,
)

private data class DashboardData(
    val user: UserEntity?,
    val categories: List<CategoryEntity>,
    val transactions: List<CanonicalTransactionEntity>,
    val candidates: List<TransactionCandidateEntity>,
    val inboxItems: List<InboxItemEntity>,
    val suggestedTxns: List<CanonicalTransactionEntity>,
    val budget: BudgetEntity?,
    val monthlySpent: Long,
    val weeklySpent: Long,
)

private data class ViewSelection(
    val isSeeding: Boolean,
    val tab: HomeTab,
    val selectedReviewRowId: String?,
    val selectedTransactionId: String?,
    val merchantDrafts: Map<String, String>,
    val amountDrafts: Map<String, String>,
    val categoryDrafts: Map<String, String?>,
    val transactionMerchantDrafts: Map<String, String>,
    val transactionNotesDrafts: Map<String, String>,
    val manualEntry: ManualEntryDraft,
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
    private val selectedReviewRowId = MutableStateFlow<String?>(null)
    private val selectedTransactionId = MutableStateFlow<String?>(null)
    private val reviewMerchantDrafts = MutableStateFlow<Map<String, String>>(emptyMap())
    private val reviewAmountDrafts = MutableStateFlow<Map<String, String>>(emptyMap())
    private val reviewCategoryDrafts = MutableStateFlow<Map<String, String?>>(emptyMap())
    private val transactionMerchantDrafts = MutableStateFlow<Map<String, String>>(emptyMap())
    private val transactionNotesDrafts = MutableStateFlow<Map<String, String>>(emptyMap())
    private val manualEntry = MutableStateFlow(ManualEntryDraft())

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
            repository.observeCategories(),
            repository.observeRecentTransactions(),
            repository.observeRecentTransactionCandidates(),
            repository.observePendingInboxItems(),
        ) { user, cats, txns, cands, inbox ->
            arrayOf<Any?>(user, cats, txns, cands, inbox)
        },
        combine(
            repository.observeSuggestedTransactions(),
            monthlyBudgetFlow,
            monthlySpendFlow,
            weeklySpendFlow,
        ) { suggested, b, m, w ->
            arrayOf<Any?>(suggested, b, m, w)
        },
    ) { entities, periods ->
        @Suppress("UNCHECKED_CAST")
        DashboardData(
            user = entities[0] as UserEntity?,
            categories = entities[1] as List<CategoryEntity>,
            transactions = entities[2] as List<CanonicalTransactionEntity>,
            candidates = entities[3] as List<TransactionCandidateEntity>,
            inboxItems = entities[4] as List<InboxItemEntity>,
            suggestedTxns = periods[0] as List<CanonicalTransactionEntity>,
            budget = periods[1] as BudgetEntity?,
            monthlySpent = periods[2] as Long,
            weeklySpent = periods[3] as Long,
        )
    }

    private val viewSelection: Flow<ViewSelection> = combine(
        combine(
            isSeeding,
            selectedTab,
            selectedReviewRowId,
            selectedTransactionId,
            manualEntry,
        ) { seeding, tab, rid, tid, manual ->
            arrayOf<Any?>(seeding, tab, rid, tid, manual)
        },
        combine(
            reviewMerchantDrafts,
            reviewAmountDrafts,
            reviewCategoryDrafts,
            transactionMerchantDrafts,
            transactionNotesDrafts,
        ) { rm, ra, rc, tm, tn ->
            arrayOf<Any?>(rm, ra, rc, tm, tn)
        },
    ) { selection, drafts ->
        @Suppress("UNCHECKED_CAST")
        ViewSelection(
            isSeeding = selection[0] as Boolean,
            tab = selection[1] as HomeTab,
            selectedReviewRowId = selection[2] as String?,
            selectedTransactionId = selection[3] as String?,
            manualEntry = selection[4] as ManualEntryDraft,
            merchantDrafts = drafts[0] as Map<String, String>,
            amountDrafts = drafts[1] as Map<String, String>,
            categoryDrafts = drafts[2] as Map<String, String?>,
            transactionMerchantDrafts = drafts[3] as Map<String, String>,
            transactionNotesDrafts = drafts[4] as Map<String, String>,
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

    fun selectReviewRow(id: String) {
        selectedReviewRowId.value = id
    }

    fun updateReviewMerchantDraft(id: String, value: String) {
        reviewMerchantDrafts.value = reviewMerchantDrafts.value + (id to value)
    }

    fun updateReviewAmountDraft(id: String, value: String) {
        reviewAmountDrafts.value = reviewAmountDrafts.value + (id to value)
    }

    fun updateReviewCategoryDraft(id: String, categoryId: String?) {
        reviewCategoryDrafts.value = reviewCategoryDrafts.value + (id to categoryId)
    }

    fun confirmReviewRow(id: String, source: ReviewSource) {
        viewModelScope.launch {
            val merchant = reviewMerchantDrafts.value[id]
            val amountMinor = reviewAmountDrafts.value[id]?.let(::parseRupeesToMinor)
            val categoryId = reviewCategoryDrafts.value[id]
            when (source) {
                ReviewSource.INBOX -> repository.confirmInboxItem(
                    inboxItemId = id,
                    merchantNameOverride = merchant,
                    amountMinorOverride = amountMinor,
                    categoryIdOverride = categoryId,
                )
                ReviewSource.SUGGESTED -> repository.confirmSuggestedTransaction(
                    transactionId = id,
                    merchantNameOverride = merchant,
                    amountMinorOverride = amountMinor,
                    categoryIdOverride = categoryId,
                )
            }
            clearReviewDrafts(id)
        }
    }

    fun dismissReviewRow(id: String, source: ReviewSource) {
        viewModelScope.launch {
            when (source) {
                ReviewSource.INBOX -> repository.dismissInboxItem(id)
                ReviewSource.SUGGESTED -> repository.dismissSuggestedTransaction(id)
            }
            clearReviewDrafts(id)
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

    // Manual entry
    fun openManualEntry() {
        manualEntry.value = ManualEntryDraft(isOpen = true)
    }

    fun closeManualEntry() {
        manualEntry.value = ManualEntryDraft()
    }

    fun updateManualEntry(transform: ManualEntryDraft.() -> ManualEntryDraft) {
        manualEntry.value = manualEntry.value.transform()
    }

    fun submitManualEntry() {
        val draft = manualEntry.value
        val amountMinor = parseRupeesToMinor(draft.amountRupees)
        if (amountMinor == null || amountMinor <= 0L) {
            manualEntry.value = draft.copy(error = "Enter a valid amount")
            return
        }
        if (draft.merchant.isBlank()) {
            manualEntry.value = draft.copy(error = "Merchant or payee is required")
            return
        }
        manualEntry.value = draft.copy(isSaving = true, error = null)
        viewModelScope.launch {
            repository.createManualTransaction(
                merchantName = draft.merchant,
                amountMinor = amountMinor,
                mode = draft.mode,
                categoryId = draft.categoryId,
                notes = draft.notes.ifBlank { null },
            )
            manualEntry.value = ManualEntryDraft()
        }
    }

    private fun clearReviewDrafts(id: String) {
        reviewMerchantDrafts.value = reviewMerchantDrafts.value - id
        reviewAmountDrafts.value = reviewAmountDrafts.value - id
        reviewCategoryDrafts.value = reviewCategoryDrafts.value - id
        if (selectedReviewRowId.value == id) selectedReviewRowId.value = null
    }

    private fun toUiState(
        data: DashboardData,
        selection: ViewSelection,
        date: LocalDate,
    ): HomeUiState {
        val candidatesById = data.candidates.associateBy { it.id }
        val categoryOptions = data.categories.map { CategoryOption(it.id, it.name) }
        val reviewRows = buildReviewRows(data, candidatesById, selection)

        val transactionRows = data.transactions
            .filter { it.status != CanonicalTransactionStatus.IGNORED }
            .take(20)
            .map { transaction ->
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
            pendingReviewCount = reviewRows.size,
            selectedTab = selection.tab,
            selectedReviewRowId = selection.selectedReviewRowId ?: reviewRows.firstOrNull()?.id,
            selectedTransactionId = selection.selectedTransactionId ?: transactionRows.firstOrNull()?.id,
            reviewRows = reviewRows,
            recentTransactions = transactionRows,
            categories = categoryOptions,
            manualEntry = selection.manualEntry,
            dashboard = buildDashboard(data, reviewRows.size, date),
            isSeeding = selection.isSeeding,
        )
    }

    private fun buildReviewRows(
        data: DashboardData,
        candidatesById: Map<String, TransactionCandidateEntity>,
        selection: ViewSelection,
    ): List<HomeReviewRow> {
        val inboxRows = data.inboxItems.map { inboxItem ->
            val candidate = candidatesById[inboxItem.transactionCandidateId]
            val rawAmount = candidate?.amountMinor
            HomeReviewRow(
                id = inboxItem.id,
                source = ReviewSource.INBOX,
                merchant = candidate?.toEntityName ?: "Review transaction",
                merchantDraft = selection.merchantDrafts[inboxItem.id]
                    ?: candidate?.toEntityName.orEmpty(),
                amountLabel = rawAmount?.let(::formatRowAmount) ?: "—",
                amountDraftRupees = selection.amountDrafts[inboxItem.id]
                    ?: rawAmount?.let { (it / 100.0).toRupeeInput() } ?: "",
                categoryId = null,
                categoryIdDraft = selection.categoryDrafts[inboxItem.id],
                subline = listOfNotNull(
                    candidate?.mode?.name?.replace('_', ' '),
                    candidate?.occurredAt?.take(10),
                ).joinToString(" • ").ifBlank { inboxItem.createdAt.take(10) },
                reasonLabel = inboxItem.reasonCode.name.replace('_', ' '),
            )
        }
        val suggestedRows = data.suggestedTxns.map { txn ->
            HomeReviewRow(
                id = txn.id,
                source = ReviewSource.SUGGESTED,
                merchant = txn.merchantName ?: "Auto-captured transaction",
                merchantDraft = selection.merchantDrafts[txn.id] ?: txn.merchantName.orEmpty(),
                amountLabel = formatRowAmount(txn.amountMinor),
                amountDraftRupees = selection.amountDrafts[txn.id]
                    ?: (txn.amountMinor / 100.0).toRupeeInput(),
                categoryId = txn.categoryId,
                categoryIdDraft = selection.categoryDrafts.getOrElse(txn.id) { txn.categoryId },
                subline = listOfNotNull(
                    txn.mode?.name?.replace('_', ' '),
                    txn.sourceSummary,
                    txn.occurredAt.take(10),
                ).joinToString(" • "),
                reasonLabel = "AUTO CAPTURED",
            )
        }
        return inboxRows + suggestedRows
    }

    private fun buildDashboard(
        data: DashboardData,
        pendingReviewCount: Int,
        date: LocalDate,
    ): HomeDashboard {
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

        val recents = data.transactions
            .filter { it.status != CanonicalTransactionStatus.IGNORED }
            .take(5)
            .map { txn ->
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
            pendingReviewCount = pendingReviewCount,
            recentTransactions = recents,
            hasAnyTransactions = data.transactions.any { it.status != CanonicalTransactionStatus.IGNORED },
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

    private fun parseRupeesToMinor(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        val cleaned = raw.trim().replace(",", "")
        val rupees = cleaned.toDoubleOrNull() ?: return null
        return (rupees * 100.0).toLong()
    }

    private fun Double.toRupeeInput(): String =
        if (this % 1.0 == 0.0) toLong().toString() else "%.2f".format(this)

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
