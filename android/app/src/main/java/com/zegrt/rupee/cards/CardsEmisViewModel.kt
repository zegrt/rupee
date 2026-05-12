package com.zegrt.rupee.cards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.zegrt.rupee.data.local.entity.CreditCardEntity
import com.zegrt.rupee.data.local.entity.EmiPlanEntity
import com.zegrt.rupee.data.repository.LocalFinanceRepository
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Currency
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CardRow(
    val id: String,
    val displayName: String,
    val subtitle: String,
    val outstandingLabel: String?,
    val limitLabel: String?,
    val dueLabel: String?,
    val excludeFromExpenseTotals: Boolean,
)

data class EmiRow(
    val id: String,
    val name: String,
    val monthlyLabel: String,
    val tenureLabel: String?,
    val nextDueLabel: String?,
    val outstandingLabel: String?,
)

data class EmiDraft(
    val isOpen: Boolean = false,
    val name: String = "",
    val monthlyRupees: String = "",
    val tenureMonths: String = "",
    val nextDueDate: String = "",
    val notes: String = "",
    val isSaving: Boolean = false,
    val error: String? = null,
)

data class CardDueDraft(
    val isOpen: Boolean = false,
    val cardId: String = "",
    val cardName: String = "",
    val amountRupees: String = "",
    val dueDate: String = "",
    val isSaving: Boolean = false,
    val error: String? = null,
)

data class CardsEmisUiState(
    val cards: List<CardRow> = emptyList(),
    val emis: List<EmiRow> = emptyList(),
    val emiDraft: EmiDraft = EmiDraft(),
    val cardDueDraft: CardDueDraft = CardDueDraft(),
)

class CardsEmisViewModel(
    private val repository: LocalFinanceRepository,
) : ViewModel() {

    private val moneyFormatter = NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
        maximumFractionDigits = 0
        currency = Currency.getInstance("INR")
    }
    private val dueDateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")

    private val emiDraft = MutableStateFlow(EmiDraft())
    private val cardDueDraft = MutableStateFlow(CardDueDraft())

    val uiState: StateFlow<CardsEmisUiState> = combine(
        repository.observeCards(),
        repository.observeEmiPlans(),
        emiDraft,
        cardDueDraft,
    ) { cards, emis, draft, due ->
        CardsEmisUiState(
            cards = cards.map(::toCardRow),
            emis = emis.map(::toEmiRow),
            emiDraft = draft,
            cardDueDraft = due,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CardsEmisUiState(),
    )

    fun openEmiDraft() {
        emiDraft.value = EmiDraft(isOpen = true)
    }

    fun closeEmiDraft() {
        emiDraft.value = EmiDraft()
    }

    fun updateEmiDraft(transform: EmiDraft.() -> EmiDraft) {
        emiDraft.value = emiDraft.value.transform()
    }

    fun submitEmiDraft() {
        val draft = emiDraft.value
        val monthlyMinor = draft.monthlyRupees.filter { it.isDigit() }.toLongOrNull()?.times(100)
        if (draft.name.isBlank()) {
            emiDraft.value = draft.copy(error = "Enter a plan name")
            return
        }
        if (monthlyMinor == null || monthlyMinor <= 0L) {
            emiDraft.value = draft.copy(error = "Enter a valid monthly amount")
            return
        }
        val tenure = draft.tenureMonths.filter { it.isDigit() }.toIntOrNull()
        val nextDueIso = parseDueDate(draft.nextDueDate)
        if (draft.nextDueDate.isNotBlank() && nextDueIso == null) {
            emiDraft.value = draft.copy(error = "Use d-MMM-yyyy or yyyy-MM-dd")
            return
        }
        emiDraft.value = draft.copy(isSaving = true, error = null)
        viewModelScope.launch {
            repository.addEmiPlan(
                name = draft.name,
                monthlyAmountMinor = monthlyMinor,
                remainingTenureMonths = tenure,
                nextDueAt = nextDueIso,
                notes = draft.notes.ifBlank { null },
            )
            emiDraft.value = EmiDraft()
        }
    }

    fun removeEmi(id: String) {
        viewModelScope.launch { repository.removeEmiPlan(id) }
    }

    fun openCardDueDraft(cardId: String) {
        val card = uiState.value.cards.firstOrNull { it.id == cardId } ?: return
        cardDueDraft.value = CardDueDraft(
            isOpen = true,
            cardId = cardId,
            cardName = card.displayName,
            amountRupees = "",
            dueDate = "",
        )
    }

    fun closeCardDueDraft() {
        cardDueDraft.value = CardDueDraft()
    }

    fun updateCardDueDraft(transform: CardDueDraft.() -> CardDueDraft) {
        cardDueDraft.value = cardDueDraft.value.transform()
    }

    fun submitCardDueDraft() {
        val draft = cardDueDraft.value
        if (draft.cardId.isBlank()) return
        val amountMinor = draft.amountRupees.filter { it.isDigit() }.toLongOrNull()?.times(100)
        val dueIso = parseDueDate(draft.dueDate)
        if (amountMinor == null) {
            cardDueDraft.value = draft.copy(error = "Enter a valid amount")
            return
        }
        if (draft.dueDate.isNotBlank() && dueIso == null) {
            cardDueDraft.value = draft.copy(error = "Use d-MMM-yyyy or yyyy-MM-dd")
            return
        }
        cardDueDraft.value = draft.copy(isSaving = true, error = null)
        viewModelScope.launch {
            repository.setCreditCardDue(draft.cardId, amountMinor, dueIso)
            cardDueDraft.value = CardDueDraft()
        }
    }

    private fun toCardRow(card: CreditCardEntity): CardRow = CardRow(
        id = card.id,
        displayName = card.displayName,
        subtitle = listOfNotNull(
            card.providerName,
            card.network,
            card.maskedIdentifier,
        ).joinToString(" • ").ifBlank { "Credit card" },
        outstandingLabel = card.currentOutstandingMinor?.let { "Outstanding: ${formatMinor(it)}" },
        limitLabel = card.creditLimitMinor?.let { "Limit: ${formatMinor(it)}" },
        dueLabel = card.statementDueAmountMinor?.let { amount ->
            val due = card.statementDueDate?.let { formatDueLabel(it) }
            if (due != null) "Due ${formatMinor(amount)} on $due" else "Due ${formatMinor(amount)}"
        },
        excludeFromExpenseTotals = card.excludeFromExpenseTotals,
    )

    fun setCardExcludeFromExpenseTotals(cardId: String, exclude: Boolean) {
        viewModelScope.launch {
            repository.setCardExcludeFromExpenseTotals(cardId, exclude)
        }
    }

    private fun toEmiRow(plan: EmiPlanEntity): EmiRow = EmiRow(
        id = plan.id,
        name = plan.name,
        monthlyLabel = "${formatMinor(plan.monthlyAmountMinor)} / month",
        tenureLabel = plan.remainingTenureMonths?.let { "$it month${if (it == 1) "" else "s"} left" },
        nextDueLabel = plan.nextDueAt?.let { "Next due: ${formatDueLabel(it)}" },
        outstandingLabel = plan.totalOutstandingMinor?.let { "Outstanding: ${formatMinor(it)}" },
    )

    private fun formatMinor(minor: Long): String = moneyFormatter.format(minor / 100.0)

    private fun formatDueLabel(iso: String): String = try {
        LocalDate.parse(iso.take(10)).format(dueDateFormatter)
    } catch (_: DateTimeParseException) {
        iso
    }

    private fun parseDueDate(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return null
        return runCatching { LocalDate.parse(trimmed).toString() }
            .recoverCatching { LocalDate.parse(trimmed, dueDateFormatter).toString() }
            .getOrNull()
    }
}

class CardsEmisViewModelFactory(
    private val repository: LocalFinanceRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(CardsEmisViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }
        return CardsEmisViewModel(repository) as T
    }
}
