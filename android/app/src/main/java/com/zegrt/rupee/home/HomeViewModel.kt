package com.zegrt.rupee.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.zegrt.rupee.budget.DuesAlertManager
import com.zegrt.rupee.data.local.entity.BudgetEntity
import com.zegrt.rupee.data.local.entity.CanonicalTransactionEntity
import com.zegrt.rupee.data.local.entity.CanonicalTransactionStatus
import com.zegrt.rupee.data.local.entity.CanonicalTransactionType
import com.zegrt.rupee.data.local.entity.CategoryEntity
import com.zegrt.rupee.data.local.entity.CreditCardEntity
import com.zegrt.rupee.data.local.entity.EmiPlanEntity
import com.zegrt.rupee.data.local.entity.MerchantTrustRuleEntity
import com.zegrt.rupee.data.local.entity.RecurringPatternEntity
import com.zegrt.rupee.data.local.entity.Mode
import com.zegrt.rupee.data.local.dao.InboxItemWithCandidate
import com.zegrt.rupee.data.local.entity.UserEntity
import com.zegrt.rupee.budget.BudgetAlertManager
import com.zegrt.rupee.data.repository.LocalFinanceRepository
import com.zegrt.rupee.ingestion.MerchantNameUtils
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.YearMonth
import java.time.format.DateTimeFormatter
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
    CALENDAR,
    SETTINGS,
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
    val alwaysTrust: Boolean,
    val mergeTargetId: String? = null,
)

data class HomeTransactionRow(
    val id: String,
    val headline: String,
    val subline: String,
    val amountLabel: String,
    val notes: String,
    val merchant: String,
    val merchantDraft: String,
    val notesDraft: String,
    val categoryId: String?,
    val categoryIdDraft: String?,
    val categoryLabel: String?,
    val isIncome: Boolean = false,
    // Draft of the type for the edit form's segmented toggle. Equal to the
    // persisted type unless the user has changed it in the current edit session.
    val typeDraft: CanonicalTransactionType = CanonicalTransactionType.EXPENSE,
    // True when a `MerchantTrustRuleEntity` matches this row's merchant (by
    // cleaned-name comparison). Drives the Transactions edit sheet's "Always
    // trust {merchant}" toggle state — toggling fires an immediate
    // repository.setMerchantTrust call rather than the staged confirm-time
    // path the Inbox uses.
    val alwaysTrust: Boolean = false,
)

data class HomeRecentRow(
    val id: String,
    val merchant: String,
    val subline: String,
    val amountLabel: String,
    val isSuggested: Boolean,
    val isIncome: Boolean = false,
)

data class CategoryOption(
    val id: String,
    val name: String,
)

enum class ManualEntryType { EXPENSE, INCOME }

data class ManualEntryDraft(
    val isOpen: Boolean = false,
    val type: ManualEntryType = ManualEntryType.EXPENSE,
    val merchant: String = "",
    val amountRupees: String = "",
    val mode: Mode = Mode.UPI,
    val categoryId: String? = null,
    val notes: String = "",
    val isSaving: Boolean = false,
    val error: String? = null,
)

/**
 * "Tap to fill" suggestion shown above the merchant field on the manual
 * entry sheet. One per recently-used merchant; tapping pre-fills merchant
 * + category + mode in one gesture, hitting the 20s-target manual-entry
 * goal (backlog MANUAL-FAST).
 */
data class ManualEntrySuggestion(
    val merchant: String,
    val categoryId: String?,
    val mode: Mode?,
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
    // Income (v0.13.7). Null when zero — UI hides the line entirely so users
    // without any captured income don't see a permanent "+₹0" zero state.
    val monthlyIncomeLabel: String? = null,
    val pendingReviewCount: Int = 0,
    val recentTransactions: List<HomeRecentRow> = emptyList(),
    val hasAnyTransactions: Boolean = false,
    val upcomingDues: List<HomeDueRow> = emptyList(),
)

data class HomeDueRow(
    val id: String,
    val title: String,
    val amountLabel: String,
    val dueLabel: String,
    val daysAway: Int,
    val isOverdue: Boolean,
    val kind: HomeDueKind,
)

enum class HomeDueKind { CARD, EMI, RECURRING }

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
    val manualEntrySuggestions: List<ManualEntrySuggestion> = emptyList(),
    val dashboard: HomeDashboard = HomeDashboard(),
    val isSeeding: Boolean = true,
    // DIAG-CAPTURE-TOGGLE — surfaces the persistent banner on Home + Inbox
    // when capture is on, so testers know their notification text is being
    // saved locally for sharing.
    val diagnosticCaptureEnabled: Boolean = false,
)

private data class DashboardData(
    val user: UserEntity?,
    val categories: List<CategoryEntity>,
    val transactions: List<CanonicalTransactionEntity>,
    // Joined at the DB via @Relation — see InboxItemWithCandidate. Replaces
    // the previous parallel-flow setup (inbox + recent-candidates) which
    // husked when the candidate window rotated past an inbox row.
    val inboxItems: List<InboxItemWithCandidate>,
    // Active merchant trust rules. Joined here so HomeTransactionRow.alwaysTrust
    // reflects current DB truth; the Transactions edit sheet's toggle reads
    // and flips this directly.
    val trustRules: List<MerchantTrustRuleEntity> = emptyList(),
    val suggestedTxns: List<CanonicalTransactionEntity>,
    val budget: BudgetEntity?,
    val monthlySpent: Long,
    val weeklySpent: Long,
    val monthlyIncome: Long,
    val cards: List<CreditCardEntity>,
    val emis: List<EmiPlanEntity>,
    val recurringPatterns: List<RecurringPatternEntity>,
)

private data class ReviewDraftBundle(
    val merchant: Map<String, String>,
    val amount: Map<String, String>,
    val category: Map<String, String?>,
    val alwaysTrust: Set<String>,
)

private data class ViewSelection(
    val isSeeding: Boolean,
    val tab: HomeTab,
    val selectedReviewRowId: String?,
    val selectedTransactionId: String?,
    val merchantDrafts: Map<String, String>,
    val amountDrafts: Map<String, String>,
    val categoryDrafts: Map<String, String?>,
    val alwaysTrust: Set<String>,
    val transactionMerchantDrafts: Map<String, String>,
    val transactionNotesDrafts: Map<String, String>,
    val transactionCategoryDrafts: Map<String, String?>,
    val transactionTypeDrafts: Map<String, CanonicalTransactionType>,
    val manualEntry: ManualEntryDraft,
    val reviewMergeTargets: Map<String, String>,
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val repository: LocalFinanceRepository,
    private val clock: () -> LocalDate = { LocalDate.now() },
    private val timeClock: () -> LocalTime = { LocalTime.now() },
    private val budgetAlertManager: BudgetAlertManager? = null,
    private val duesAlertManager: DuesAlertManager? = null,
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
    private val reviewAlwaysTrust = MutableStateFlow<Set<String>>(emptySet())
    private val transactionMerchantDrafts = MutableStateFlow<Map<String, String>>(emptyMap())
    private val transactionNotesDrafts = MutableStateFlow<Map<String, String>>(emptyMap())
    private val transactionCategoryDrafts = MutableStateFlow<Map<String, String?>>(emptyMap())
    private val transactionTypeDrafts = MutableStateFlow<Map<String, CanonicalTransactionType>>(emptyMap())
    private val manualEntry = MutableStateFlow(ManualEntryDraft())
    private val reviewMergeTargets = MutableStateFlow<Map<String, String>>(emptyMap())

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
    private val monthlyIncomeFlow: Flow<Long> = today.flatMapLatest { date ->
        val month = YearMonth.from(date)
        repository.observeReceivedInPeriod(
            fromIso = month.atDay(1).toString(),
            untilIso = month.plusMonths(1).atDay(1).toString(),
        )
    }

    private val obligationsBundle: Flow<Triple<List<CreditCardEntity>, List<EmiPlanEntity>, List<RecurringPatternEntity>>> = combine(
        repository.observeCards(),
        repository.observeEmiPlans(),
        repository.observeRecurringPatterns(),
    ) { cards, emis, patterns -> Triple(cards, emis, patterns) }

    private val dashboardData: Flow<DashboardData> = combine(
        combine(
            repository.observeUser(),
            repository.observeCategories(),
            repository.observeRecentTransactions(),
            repository.observePendingInboxItemsWithCandidates(),
        ) { user, cats, txns, inbox ->
            arrayOf<Any?>(user, cats, txns, inbox)
        },
        combine(
            repository.observeSuggestedTransactions(),
            monthlyBudgetFlow,
            monthlySpendFlow,
            weeklySpendFlow,
            monthlyIncomeFlow,
        ) { suggested, b, m, w, inc ->
            arrayOf<Any?>(suggested, b, m, w, inc)
        },
        obligationsBundle,
        repository.observeMerchantTrustRules(),
    ) { entities, periods, dues, trustRules ->
        @Suppress("UNCHECKED_CAST")
        DashboardData(
            user = entities[0] as UserEntity?,
            categories = entities[1] as List<CategoryEntity>,
            transactions = entities[2] as List<CanonicalTransactionEntity>,
            inboxItems = entities[3] as List<InboxItemWithCandidate>,
            trustRules = trustRules,
            suggestedTxns = periods[0] as List<CanonicalTransactionEntity>,
            budget = periods[1] as BudgetEntity?,
            monthlySpent = periods[2] as Long,
            weeklySpent = periods[3] as Long,
            monthlyIncome = periods[4] as Long,
            cards = dues.first,
            emis = dues.second,
            recurringPatterns = dues.third,
        )
    }

    private val reviewDrafts: Flow<ReviewDraftBundle> = combine(
        reviewMerchantDrafts,
        reviewAmountDrafts,
        reviewCategoryDrafts,
        reviewAlwaysTrust,
    ) { m, a, c, t -> ReviewDraftBundle(m, a, c, t) }

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
            reviewDrafts,
            transactionMerchantDrafts,
            transactionNotesDrafts,
            transactionCategoryDrafts,
            transactionTypeDrafts,
        ) { review, tm, tn, tc, tt ->
            arrayOf<Any?>(review, tm, tn, tc, tt)
        },
        reviewMergeTargets,
    ) { selection, drafts, merges ->
        @Suppress("UNCHECKED_CAST")
        val review = drafts[0] as ReviewDraftBundle
        @Suppress("UNCHECKED_CAST")
        ViewSelection(
            isSeeding = selection[0] as Boolean,
            tab = selection[1] as HomeTab,
            selectedReviewRowId = selection[2] as String?,
            selectedTransactionId = selection[3] as String?,
            manualEntry = selection[4] as ManualEntryDraft,
            merchantDrafts = review.merchant,
            amountDrafts = review.amount,
            categoryDrafts = review.category,
            alwaysTrust = review.alwaysTrust,
            transactionMerchantDrafts = drafts[1] as Map<String, String>,
            transactionNotesDrafts = drafts[2] as Map<String, String>,
            transactionCategoryDrafts = drafts[3] as Map<String, String?>,
            transactionTypeDrafts = drafts[4] as Map<String, CanonicalTransactionType>,
            reviewMergeTargets = merges,
        )
    }

    val uiState: StateFlow<HomeUiState> = combine(
        dashboardData,
        viewSelection,
        repository.observeDiagnosticCaptureEnabled(),
    ) { data, selection, diagCapture ->
        toUiState(data, selection, today.value).copy(diagnosticCaptureEnabled = diagCapture)
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
            runCatching { repository.refreshRecurringPatterns(clock()) }
            runCatching { duesAlertManager?.checkAndNotify(repository, clock()) }
        }
    }

    fun refreshOnResume() {
        val now = clock()
        if (today.value != now) today.value = now
        viewModelScope.launch {
            repository.ensureMonthlyBudgetForToday(now)
            runCatching { repository.refreshRecurringPatterns(now) }
            runCatching { duesAlertManager?.checkAndNotify(repository, now) }
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

    fun toggleAlwaysTrust(id: String) {
        val current = reviewAlwaysTrust.value
        reviewAlwaysTrust.value = if (id in current) current - id else current + id
    }

    fun selectMergeTarget(reviewId: String, txnId: String?) {
        reviewMergeTargets.value = if (txnId != null)
            reviewMergeTargets.value + (reviewId to txnId)
        else
            reviewMergeTargets.value - reviewId
    }

    fun confirmReviewRow(id: String, source: ReviewSource) {
        viewModelScope.launch {
            val merchant = reviewMerchantDrafts.value[id]
            val amountMinor = reviewAmountDrafts.value[id]?.let(::parseRupeesToMinor)
            val categoryId = reviewCategoryDrafts.value[id]
            val trust = id in reviewAlwaysTrust.value
            when (source) {
                ReviewSource.INBOX -> {
                    val mergeTarget = reviewMergeTargets.value[id]
                    if (mergeTarget != null) {
                        repository.confirmInboxItemMergedWith(id, mergeTarget)
                    } else {
                        repository.confirmInboxItem(
                            inboxItemId = id,
                            merchantNameOverride = merchant,
                            amountMinorOverride = amountMinor,
                            categoryIdOverride = categoryId,
                            addTrustRule = trust,
                        )
                    }
                }
                ReviewSource.SUGGESTED -> repository.confirmSuggestedTransaction(
                    transactionId = id,
                    merchantNameOverride = merchant,
                    amountMinorOverride = amountMinor,
                    categoryIdOverride = categoryId,
                    addTrustRule = trust,
                )
            }
            clearReviewDrafts(id)
            checkBudgetAlert()
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

    fun updateTransactionCategoryDraft(id: String, categoryId: String?) {
        transactionCategoryDrafts.value = transactionCategoryDrafts.value + (id to categoryId)
    }

    fun updateTransactionTypeDraft(id: String, type: CanonicalTransactionType) {
        transactionTypeDrafts.value = transactionTypeDrafts.value + (id to type)
    }

    fun saveTransactionEdits(id: String) {
        val categoryDirty = transactionCategoryDrafts.value.containsKey(id)
        val categoryId = transactionCategoryDrafts.value[id]
        val typeOverride = transactionTypeDrafts.value[id]
        viewModelScope.launch {
            repository.updateTransactionDetails(
                transactionId = id,
                merchantName = transactionMerchantDrafts.value[id].orEmpty(),
                notes = transactionNotesDrafts.value[id].orEmpty(),
                categoryId = categoryId,
                applyCategory = categoryDirty,
                type = typeOverride,
            )
            // Clear the type draft after persisting so a future open of the same
            // row reads the new persisted type rather than the stale draft.
            transactionTypeDrafts.value = transactionTypeDrafts.value - id
        }
    }

    fun deleteTransaction(id: String) {
        viewModelScope.launch {
            repository.deleteTransaction(id)
            if (selectedTransactionId.value == id) selectedTransactionId.value = null
        }
    }

    /**
     * Toggle the "Always trust this merchant" rule from the Transactions edit
     * sheet. Unlike the Inbox row (which stages the intent until Confirm fires),
     * here the transaction is already confirmed — the rule is persisted
     * immediately. The flow-joined trust-rule list in `DashboardData` flips
     * `HomeTransactionRow.alwaysTrust` on the next emission so the UI reflects
     * DB truth without extra wiring.
     *
     * Reads the txn's persisted merchant + category (not the unsaved
     * draft) so toggling doesn't silently capture mid-edit garbage. Flip
     * direction is derived from the row's currently-rendered `alwaysTrust`;
     * `setMerchantTrust` is idempotent (covered by
     * `MerchantTrustRepositoryTest`) so an out-of-date row at worst causes
     * a redundant no-op insert/delete.
     */
    fun toggleTransactionAlwaysTrust(id: String) {
        viewModelScope.launch {
            val txn = repository.getTransactionById(id) ?: return@launch
            val merchant = txn.merchantName?.takeIf { it.isNotBlank() } ?: return@launch
            val rowAlwaysTrust = uiState.value.recentTransactions
                .firstOrNull { it.id == id }?.alwaysTrust ?: false
            repository.setMerchantTrust(
                merchant = merchant,
                autoCategoryId = txn.categoryId,
                trust = !rowAlwaysTrust,
            )
        }
    }

    fun closeTransactionDetail() {
        selectedTransactionId.value = null
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
                type = if (draft.type == ManualEntryType.INCOME)
                    CanonicalTransactionType.INCOME else CanonicalTransactionType.EXPENSE,
            )
            manualEntry.value = ManualEntryDraft()
            checkBudgetAlert()
        }
    }

    private suspend fun checkBudgetAlert() {
        budgetAlertManager?.checkAndNotify(repository, clock())
    }

    private fun clearReviewDrafts(id: String) {
        reviewMerchantDrafts.value = reviewMerchantDrafts.value - id
        reviewAmountDrafts.value = reviewAmountDrafts.value - id
        reviewCategoryDrafts.value = reviewCategoryDrafts.value - id
        reviewAlwaysTrust.value = reviewAlwaysTrust.value - id
        reviewMergeTargets.value = reviewMergeTargets.value - id
        if (selectedReviewRowId.value == id) selectedReviewRowId.value = null
    }

    private fun toUiState(
        data: DashboardData,
        selection: ViewSelection,
        date: LocalDate,
    ): HomeUiState {
        val categoryOptions = data.categories.map { CategoryOption(it.id, it.name) }
        val reviewRows = buildReviewRows(data, selection)

        val categoryLabelById = data.categories.associate { it.id to it.name }
        // Pre-clean trust-rule patterns once so the alwaysTrust lookup below
        // doesn't recompute for every transaction.
        val trustPatterns: Set<String> = data.trustRules
            .map { it.merchantPattern.lowercase() }
            .toSet()
        val transactionRows = data.transactions
            .filter { it.status != CanonicalTransactionStatus.IGNORED }
            .take(20)
            .map { transaction ->
                val cleanedMerchant = cleanMerchant(transaction.merchantName)
                val draftCategory = if (selection.transactionCategoryDrafts.containsKey(transaction.id))
                    selection.transactionCategoryDrafts[transaction.id]
                else transaction.categoryId
                val income = transaction.type == CanonicalTransactionType.INCOME
                HomeTransactionRow(
                    id = transaction.id,
                    headline = cleanedMerchant,
                    subline = listOfNotNull(
                        transaction.mode?.name?.replace('_', ' '),
                        formatOccurredAt(transaction.occurredAt),
                    ).joinToString(" • "),
                    amountLabel = if (income) "+${formatRowAmount(transaction.amountMinor)}"
                    else formatRowAmount(transaction.amountMinor),
                    notes = transaction.notes.orEmpty(),
                    merchant = cleanedMerchant,
                    merchantDraft = selection.transactionMerchantDrafts[transaction.id]
                        ?: cleanedMerchant,
                    notesDraft = selection.transactionNotesDrafts[transaction.id]
                        ?: transaction.notes.orEmpty(),
                    categoryId = transaction.categoryId,
                    categoryIdDraft = draftCategory,
                    categoryLabel = transaction.categoryId?.let { categoryLabelById[it] },
                    isIncome = income,
                    typeDraft = selection.transactionTypeDrafts[transaction.id] ?: transaction.type,
                    alwaysTrust = cleanedMerchant.lowercase() in trustPatterns,
                )
            }

        return HomeUiState(
            userName = data.user?.displayName ?: "Rupee",
            pendingReviewCount = reviewRows.size,
            selectedTab = selection.tab,
            selectedReviewRowId = selection.selectedReviewRowId ?: reviewRows.firstOrNull()?.id,
            selectedTransactionId = selection.selectedTransactionId,
            reviewRows = reviewRows,
            recentTransactions = transactionRows,
            categories = categoryOptions,
            manualEntry = selection.manualEntry,
            manualEntrySuggestions = buildManualEntrySuggestions(data.transactions),
            dashboard = buildDashboard(data, reviewRows.size, date),
            isSeeding = selection.isSeeding,
        )
    }

    /**
     * MANUAL-FAST (Sprint 2): "tap to fill" chips for the manual-entry sheet.
     * Picks the user's 5 most recent unique-merchant expense transactions
     * from the last 30 days and surfaces each as a chip that fills merchant
     * + category + mode in one gesture. The 20s manual-entry target rests
     * mostly on this — most manual entries are repeats of a known merchant
     * the user already has rules for; pre-filling skips the typing.
     *
     * EXPENSE-only because the manual-entry sheet defaults to EXPENSE and
     * INCOME entries are rare and varied. Falling back to recents from
     * either side would muddy the suggestion ordering.
     */
    private fun buildManualEntrySuggestions(
        transactions: List<CanonicalTransactionEntity>,
    ): List<ManualEntrySuggestion> {
        val seen = mutableSetOf<String>()
        val out = mutableListOf<ManualEntrySuggestion>()
        for (transaction in transactions) {
            if (transaction.type != CanonicalTransactionType.EXPENSE) continue
            val merchant = cleanMerchant(transaction.merchantName)
            if (merchant.isBlank()) continue
            val key = merchant.lowercase()
            if (!seen.add(key)) continue
            out += ManualEntrySuggestion(
                merchant = merchant,
                categoryId = transaction.categoryId,
                mode = transaction.mode,
            )
            if (out.size == MAX_MANUAL_SUGGESTIONS) break
        }
        return out
    }

    private fun buildReviewRows(
        data: DashboardData,
        selection: ViewSelection,
    ): List<HomeReviewRow> {
        val inboxRows = data.inboxItems.map { inboxWithCandidate ->
            val inboxItem = inboxWithCandidate.inbox
            val candidate = inboxWithCandidate.candidate
            val rawAmount = candidate?.amountMinor
            val merchantClean = cleanMerchant(candidate?.toEntityName)
            HomeReviewRow(
                id = inboxItem.id,
                source = ReviewSource.INBOX,
                merchant = merchantClean,
                merchantDraft = selection.merchantDrafts[inboxItem.id] ?: merchantClean,
                amountLabel = rawAmount?.let(::formatRowAmount) ?: "—",
                amountDraftRupees = selection.amountDrafts[inboxItem.id]
                    ?: rawAmount?.let { (it / 100.0).toRupeeInput() } ?: "",
                categoryId = null,
                categoryIdDraft = selection.categoryDrafts[inboxItem.id],
                subline = listOfNotNull(
                    candidate?.mode?.name?.replace('_', ' '),
                    candidate?.occurredAt?.let(::formatOccurredAt),
                ).joinToString(" • ").ifBlank { formatOccurredAt(inboxItem.createdAt) },
                reasonLabel = InboxReasonCopy.forInbox(inboxItem.reasonCode, candidate),
                alwaysTrust = inboxItem.id in selection.alwaysTrust,
                mergeTargetId = selection.reviewMergeTargets[inboxItem.id],
            )
        }
        val suggestedRows = data.suggestedTxns.map { txn ->
            val merchantClean = cleanMerchant(txn.merchantName)
            HomeReviewRow(
                id = txn.id,
                source = ReviewSource.SUGGESTED,
                merchant = merchantClean,
                merchantDraft = selection.merchantDrafts[txn.id] ?: merchantClean,
                amountLabel = formatRowAmount(txn.amountMinor),
                amountDraftRupees = selection.amountDrafts[txn.id]
                    ?: (txn.amountMinor / 100.0).toRupeeInput(),
                categoryId = txn.categoryId,
                categoryIdDraft = selection.categoryDrafts.getOrElse(txn.id) { txn.categoryId },
                subline = listOfNotNull(
                    txn.mode?.name?.replace('_', ' '),
                    formatOccurredAt(txn.occurredAt),
                ).joinToString(" • "),
                reasonLabel = "AUTO CAPTURED",
                alwaysTrust = txn.id in selection.alwaysTrust,
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
                val income = txn.type == CanonicalTransactionType.INCOME
                HomeRecentRow(
                    id = txn.id,
                    merchant = cleanMerchant(txn.merchantName),
                    subline = listOfNotNull(
                        txn.mode?.name?.replace('_', ' ')?.lowercase()?.replaceFirstChar { it.uppercase() },
                        formatOccurredAt(txn.occurredAt),
                    ).joinToString(" • "),
                    amountLabel = if (income) "+${formatRowAmount(txn.amountMinor)}"
                    else formatRowAmount(txn.amountMinor),
                    isSuggested = txn.status == CanonicalTransactionStatus.SUGGESTED,
                    isIncome = income,
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
            monthlyIncomeLabel = if (data.monthlyIncome > 0L)
                "+${formatHeadlineAmount(data.monthlyIncome)}" else null,
            pendingReviewCount = pendingReviewCount,
            recentTransactions = recents,
            hasAnyTransactions = data.transactions.any { it.status != CanonicalTransactionStatus.IGNORED },
            upcomingDues = buildUpcomingDues(data, date),
        )
    }

    private fun buildUpcomingDues(data: DashboardData, today: LocalDate): List<HomeDueRow> {
        val horizon = today.plusDays(14)
        val cardDues = data.cards.mapNotNull { card ->
            val dueDate = parseDueDate(card.statementDueDate) ?: return@mapNotNull null
            val amount = card.statementDueAmountMinor ?: return@mapNotNull null
            if (dueDate.isAfter(horizon)) return@mapNotNull null
            HomeDueRow(
                id = "card-${card.id}",
                title = card.displayName,
                amountLabel = formatRowAmount(amount),
                dueLabel = dueLabelFor(today, dueDate),
                daysAway = (dueDate.toEpochDay() - today.toEpochDay()).toInt(),
                isOverdue = dueDate.isBefore(today),
                kind = HomeDueKind.CARD,
            )
        }
        val emiDues = data.emis.mapNotNull { plan ->
            val dueDate = parseDueDate(plan.nextDueAt) ?: return@mapNotNull null
            if (dueDate.isAfter(horizon)) return@mapNotNull null
            HomeDueRow(
                id = "emi-${plan.id}",
                title = plan.name,
                amountLabel = formatRowAmount(plan.monthlyAmountMinor),
                dueLabel = dueLabelFor(today, dueDate),
                daysAway = (dueDate.toEpochDay() - today.toEpochDay()).toInt(),
                isOverdue = dueDate.isBefore(today),
                kind = HomeDueKind.EMI,
            )
        }
        val recurringDues = data.recurringPatterns
            .filter { it.isConfirmed && !it.isDismissed }
            .mapNotNull { pattern ->
                val dueDate = parseDueDate(pattern.nextExpectedAt) ?: return@mapNotNull null
                if (dueDate.isAfter(horizon)) return@mapNotNull null
                HomeDueRow(
                    id = "rec-${pattern.id}",
                    title = pattern.merchantPattern,
                    amountLabel = formatRowAmount(pattern.expectedAmountMinor),
                    dueLabel = dueLabelFor(today, dueDate),
                    daysAway = (dueDate.toEpochDay() - today.toEpochDay()).toInt(),
                    isOverdue = dueDate.isBefore(today),
                    kind = HomeDueKind.RECURRING,
                )
            }
        return (cardDues + emiDues + recurringDues).sortedBy { it.daysAway }
    }

    private fun parseDueDate(iso: String?): LocalDate? {
        if (iso.isNullOrBlank()) return null
        return runCatching { LocalDate.parse(iso.take(10)) }
            .onFailure { t ->
                // DATE-PARSE-LOGGING (Sprint 4). Previously this swallowed
                // DateTimeParseException silently, so a malformed date in
                // a card/EMI/recurring row would just drop that row from
                // upcoming-dues with no failure trail. Surface to logcat
                // so a tester report ("my EMI isn't showing under
                // upcoming dues") has something to bisect against.
                Log.w("Rupee", "parseDueDate failed for iso=$iso", t)
            }
            .getOrNull()
    }

    private fun dueLabelFor(today: LocalDate, due: LocalDate): String {
        val days = (due.toEpochDay() - today.toEpochDay()).toInt()
        return when {
            days < 0 -> "Overdue ${-days}d"
            days == 0 -> "Due today"
            days == 1 -> "Due tomorrow"
            days <= 7 -> "Due in $days days"
            else -> "Due ${due.format(DateTimeFormatter.ofPattern("d MMM"))}"
        }
    }

    private fun greetingFor(name: String?): String {
        val timeOfDay = when (timeClock().hour) {
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

    private fun cleanMerchant(raw: String?): String = MerchantNameUtils.clean(raw)

    private fun formatOccurredAt(iso: String): String = try {
        val dt = Instant.parse(iso).atZone(ZoneId.systemDefault())
        DateTimeFormatter.ofPattern("d MMM, h:mm a", Locale.ENGLISH).format(dt)
    } catch (_: Exception) {
        iso.take(10)
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

    companion object {
        private const val MAX_MANUAL_SUGGESTIONS = 5
    }
}

class HomeViewModelFactory(
    private val repository: LocalFinanceRepository,
    private val clock: () -> LocalDate = { LocalDate.now() },
    private val budgetAlertManager: BudgetAlertManager? = null,
    private val duesAlertManager: DuesAlertManager? = null,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }
        return HomeViewModel(
            repository,
            clock,
            budgetAlertManager = budgetAlertManager,
            duesAlertManager = duesAlertManager,
        ) as T
    }
}
