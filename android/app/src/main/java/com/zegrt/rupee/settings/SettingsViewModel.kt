package com.zegrt.rupee.settings

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.zegrt.rupee.data.local.entity.AccountEntity
import com.zegrt.rupee.data.local.entity.AccountType
import com.zegrt.rupee.data.local.entity.BucketEntity
import com.zegrt.rupee.data.local.entity.BudgetEntity
import com.zegrt.rupee.data.local.entity.CategoryEntity
import com.zegrt.rupee.data.local.entity.MerchantTrustRuleEntity
import com.zegrt.rupee.data.local.entity.UserEntity
import com.zegrt.rupee.data.repository.LocalFinanceRepository
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val displayName: String = "",
    val displayNameDraft: String = "",
    val monthlyBudgetLabel: String = "",
    val currencyCode: String = "INR",
    val categories: List<String> = emptyList(),
    val buckets: List<String> = emptyList(),
    val trustRules: List<TrustRuleRow> = emptyList(),
    val accounts: List<AccountSettingsRow> = emptyList(),
    val notificationGranted: Boolean = false,
    val postNotificationsGranted: Boolean = true,
    val needsPostNotificationsPrompt: Boolean = false,
    val appVersion: String = "",
    val savingName: Boolean = false,
    val message: String? = null,
)

data class TrustRuleRow(
    val id: String,
    val merchantPattern: String,
    val categoryLabel: String?,
)

data class AccountSettingsRow(
    val id: String,
    val displayName: String,
    val providerName: String?,
    val typeLabel: String,
    val excludeFromExpenseTotals: Boolean,
    val excludeFromIncomeTotals: Boolean,
)

class SettingsViewModel(
    private val repository: LocalFinanceRepository,
    private val appVersion: String = "",
) : ViewModel() {
    private val nameDraft = MutableStateFlow<String?>(null)
    private val savingName = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)
    private val notificationGranted = MutableStateFlow(false)
    private val postNotificationsGranted = MutableStateFlow(true)

    private val budgetFormatter = NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
        maximumFractionDigits = 0
        currency = java.util.Currency.getInstance("INR")
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        combine(
            repository.observeUser(),
            repository.observeMonthlyTotalBudget(),
            repository.observeCategories(),
            repository.observeBuckets(),
            repository.observeMerchantTrustRules(),
        ) { user, budget, cats, buckets, rules ->
            arrayOf<Any?>(user, budget, cats, buckets, rules)
        },
        combine(nameDraft, savingName, message) { n, s, m -> Triple(n, s, m) },
        // Notification permission flips after the user toggles system settings; this flow
        // belongs in the combine so the Settings card re-renders, not just sampled inside.
        combine(notificationGranted, postNotificationsGranted) { listener, post ->
            listener to post
        },
        // Accounts list — drives the new Accounts settings screen with its
        // per-account "exclude from totals" toggles.
        repository.observeAccounts(),
    ) { entities, drafts, perms, accounts ->
        @Suppress("UNCHECKED_CAST")
        val user = entities[0] as UserEntity?
        @Suppress("UNCHECKED_CAST")
        val budget = entities[1] as BudgetEntity?
        @Suppress("UNCHECKED_CAST")
        val cats = entities[2] as List<CategoryEntity>
        @Suppress("UNCHECKED_CAST")
        val buckets = entities[3] as List<BucketEntity>
        @Suppress("UNCHECKED_CAST")
        val rules = entities[4] as List<MerchantTrustRuleEntity>
        val (nameDraftValue, savingNameValue, messageValue) = drafts
        val (granted, postGranted) = perms

        val name = user?.displayName ?: ""
        val limitMinor = budget?.limitMinor ?: 0L
        val categoryLabelById = cats.associate { it.id to it.name }
        val trustRules = rules.map { r ->
            TrustRuleRow(
                id = r.id,
                merchantPattern = r.merchantPattern,
                categoryLabel = r.autoCategoryId?.let { categoryLabelById[it] },
            )
        }
        val accountRows = accounts.map { a ->
            AccountSettingsRow(
                id = a.id,
                displayName = a.displayName,
                providerName = a.providerName,
                typeLabel = accountTypeLabel(a.accountType),
                excludeFromExpenseTotals = a.excludeFromExpenseTotals,
                excludeFromIncomeTotals = a.excludeFromIncomeTotals,
            )
        }

        SettingsUiState(
            displayName = name,
            displayNameDraft = nameDraftValue ?: name,
            monthlyBudgetLabel = if (limitMinor > 0L) budgetFormatter.format(limitMinor / 100.0) else "Not set",
            currencyCode = user?.defaultCurrencyCode ?: "INR",
            categories = cats.map { it.name },
            buckets = buckets.map { it.name },
            trustRules = trustRules,
            accounts = accountRows,
            notificationGranted = granted,
            postNotificationsGranted = postGranted,
            needsPostNotificationsPrompt = Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.TIRAMISU && !postGranted,
            appVersion = appVersion,
            savingName = savingNameValue,
            message = messageValue,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    fun updateNameDraft(value: String) {
        nameDraft.value = value
    }

    fun saveDisplayName() {
        val current = nameDraft.value ?: return
        if (current.isBlank()) return
        savingName.value = true
        viewModelScope.launch {
            repository.updateUserDisplayName(current)
            nameDraft.value = null
            savingName.value = false
            message.value = "Name saved"
        }
    }

    fun removeTrustRule(id: String) {
        viewModelScope.launch {
            repository.removeMerchantTrustRule(id)
            message.value = "Trust rule removed"
        }
    }

    fun setAccountExcludeFromExpense(accountId: String, exclude: Boolean) {
        viewModelScope.launch {
            repository.setAccountExcludeFromExpenseTotals(accountId, exclude)
        }
    }

    fun setAccountExcludeFromIncome(accountId: String, exclude: Boolean) {
        viewModelScope.launch {
            repository.setAccountExcludeFromIncomeTotals(accountId, exclude)
        }
    }

    private fun accountTypeLabel(type: AccountType): String = when (type) {
        AccountType.BANK -> "Bank"
        AccountType.CASH -> "Cash"
    }

    fun clearMessage() {
        message.value = null
    }

    fun setNotificationGranted(granted: Boolean) {
        notificationGranted.value = granted
    }

    fun setPostNotificationsGranted(granted: Boolean) {
        postNotificationsGranted.value = granted
    }
}

class SettingsViewModelFactory(
    private val repository: LocalFinanceRepository,
    private val appVersion: String = "",
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }
        return SettingsViewModel(repository, appVersion) as T
    }
}
