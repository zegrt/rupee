package com.zegrt.rupee.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.zegrt.rupee.data.repository.LocalFinanceRepository
import com.zegrt.rupee.data.repository.OnboardingSetupInput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class OnboardingStep {
    WELCOME,
    PERMISSIONS,
    SETUP,
    HOME,
}

data class PermissionCardState(
    val title: String,
    val description: String,
    val statusLabel: String,
    val isGranted: Boolean,
)

data class SetupFormState(
    val bankAccountName: String = "",
    val bankProviderName: String = "",
    val creditCardName: String = "",
    val creditCardProviderName: String = "",
    val wantsCashAccount: Boolean = false,
    val cashBalanceInput: String = "",
)

data class OnboardingUiState(
    val currentStep: OnboardingStep = OnboardingStep.WELCOME,
    val notificationPermission: PermissionCardState = PermissionCardState(
        title = "Notification access",
        description = "Lets Rupee read transaction alerts from supported apps.",
        statusLabel = "Required for best coverage",
        isGranted = false,
    ),
    val smsLaterMessage: String = "SMS reading is planned for a later build once the notification-based flow is stable.",
    val setupForm: SetupFormState = SetupFormState(),
    val setupError: String? = null,
    val isSavingSetup: Boolean = false,
)

class OnboardingViewModel(
    private val repository: LocalFinanceRepository,
    private val preferences: OnboardingPreferences,
) : ViewModel() {
    // Optimistic: assume not completed on cold start so we never block the UI thread
    // reading SharedPreferences from disk. The viewModelScope launch below resolves
    // the real value off-thread and snaps the step forward if onboarding is done.
    private val _uiState = MutableStateFlow(OnboardingUiState(currentStep = OnboardingStep.WELCOME))
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.ensureBaseData()
            if (preferences.isCompletedAsync()) {
                _uiState.update { it.copy(currentStep = OnboardingStep.HOME) }
            }
        }
    }

    fun syncPermissionState(notificationGranted: Boolean) {
        _uiState.update {
            it.copy(
                notificationPermission = it.notificationPermission.copy(
                    isGranted = notificationGranted,
                    statusLabel = if (notificationGranted) "Granted" else "Required for best coverage",
                ),
            )
        }
    }

    fun advanceFromWelcome() {
        _uiState.update { it.copy(currentStep = OnboardingStep.PERMISSIONS) }
    }

    fun continueFromPermissions() {
        _uiState.update { it.copy(currentStep = OnboardingStep.SETUP) }
    }

    fun updateBankAccountName(value: String) {
        _uiState.update { it.copy(setupForm = it.setupForm.copy(bankAccountName = value), setupError = null) }
    }

    fun updateBankProviderName(value: String) {
        _uiState.update { it.copy(setupForm = it.setupForm.copy(bankProviderName = value), setupError = null) }
    }

    fun updateCreditCardName(value: String) {
        _uiState.update { it.copy(setupForm = it.setupForm.copy(creditCardName = value), setupError = null) }
    }

    fun updateCreditCardProviderName(value: String) {
        _uiState.update { it.copy(setupForm = it.setupForm.copy(creditCardProviderName = value), setupError = null) }
    }

    fun toggleCashAccount() {
        _uiState.update {
            it.copy(
                setupForm = it.setupForm.copy(wantsCashAccount = !it.setupForm.wantsCashAccount),
                setupError = null,
            )
        }
    }

    fun updateCashBalanceInput(value: String) {
        _uiState.update { it.copy(setupForm = it.setupForm.copy(cashBalanceInput = value), setupError = null) }
    }

    fun finishSetup() {
        val form = _uiState.value.setupForm
        if (form.bankAccountName.isBlank() && form.creditCardName.isBlank() && !form.wantsCashAccount) {
            _uiState.update { it.copy(setupError = "Add at least one bank account, credit card, or cash account to continue.") }
            return
        }

        _uiState.update { it.copy(isSavingSetup = true, setupError = null) }

        viewModelScope.launch {
            val cashBalanceMinor = form.cashBalanceInput
                .trim()
                .takeIf { it.isNotEmpty() }
                ?.toDoubleOrNull()
                ?.times(100)
                ?.toLong()

            repository.completeInitialSetup(
                OnboardingSetupInput(
                    bankAccountName = form.bankAccountName,
                    bankProviderName = form.bankProviderName,
                    creditCardName = form.creditCardName,
                    creditCardProviderName = form.creditCardProviderName,
                    cashAccountEnabled = form.wantsCashAccount,
                    cashBalanceMinor = cashBalanceMinor,
                ),
            )

            preferences.setCompleted(true)
            _uiState.update { it.copy(currentStep = OnboardingStep.HOME, isSavingSetup = false) }
        }
    }
}

class OnboardingViewModelFactory(
    private val repository: LocalFinanceRepository,
    private val preferences: OnboardingPreferences,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(OnboardingViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }
        return OnboardingViewModel(repository, preferences) as T
    }
}
