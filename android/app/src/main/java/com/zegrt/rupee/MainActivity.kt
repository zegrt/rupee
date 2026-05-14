@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.zegrt.rupee

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zegrt.rupee.data.local.entity.Mode
import com.zegrt.rupee.debug.DebugScreen
import com.zegrt.rupee.debug.DebugViewModel
import com.zegrt.rupee.debug.DebugViewModelFactory
import com.zegrt.rupee.home.HomeDashboard
import com.zegrt.rupee.home.HomeRecentRow
import com.zegrt.rupee.home.HomeReviewRow
import com.zegrt.rupee.home.HomeUiState
import com.zegrt.rupee.home.HomeTab
import com.zegrt.rupee.home.HomeTransactionRow
import com.zegrt.rupee.home.HomeViewModel
import com.zegrt.rupee.home.HomeViewModelFactory
import com.zegrt.rupee.home.ManualEntryDraft
import com.zegrt.rupee.home.ReviewSource
import com.zegrt.rupee.onboarding.OnboardingStep
import com.zegrt.rupee.settings.SettingsScreen
import com.zegrt.rupee.settings.TrustRulesScreen
import com.zegrt.rupee.settings.SettingsViewModel
import com.zegrt.rupee.settings.SettingsViewModelFactory
import com.zegrt.rupee.onboarding.OnboardingUiState
import com.zegrt.rupee.onboarding.ProfileFormState
import com.zegrt.rupee.onboarding.OnboardingViewModel
import com.zegrt.rupee.onboarding.OnboardingViewModelFactory
import com.zegrt.rupee.onboarding.PermissionCardState
import com.zegrt.rupee.onboarding.PermissionStateChecker
import com.zegrt.rupee.budgets.BudgetsScreen
import com.zegrt.rupee.budgets.BudgetsUiState
import com.zegrt.rupee.budgets.BudgetsViewModel
import com.zegrt.rupee.budgets.BudgetsViewModelFactory
import com.zegrt.rupee.budgets.CategoryBudgetRow
import com.zegrt.rupee.calendar.CalendarScreen
import com.zegrt.rupee.calendar.CalendarUiState
import com.zegrt.rupee.calendar.CalendarViewModel
import com.zegrt.rupee.calendar.CalendarViewModelFactory
import com.zegrt.rupee.cards.CardDueDraft
import com.zegrt.rupee.cards.CardDueDraftSheet
import com.zegrt.rupee.cards.CardsEmisScreen
import com.zegrt.rupee.cards.CardsEmisUiState
import com.zegrt.rupee.cards.CardsEmisViewModel
import com.zegrt.rupee.cards.CardsEmisViewModelFactory
import com.zegrt.rupee.cards.EmiDraft
import com.zegrt.rupee.cards.EmiDraftSheet
import com.zegrt.rupee.debug.DebugUiState
import com.zegrt.rupee.debug.SampleNotification
import com.zegrt.rupee.home.CategoryOption
import com.zegrt.rupee.home.HomeDueKind
import com.zegrt.rupee.home.HomeDueRow
import com.zegrt.rupee.home.ManualEntryType
import com.zegrt.rupee.recap.RecapScreen
import com.zegrt.rupee.recap.RecapUiState
import com.zegrt.rupee.recap.RecapViewModel
import com.zegrt.rupee.recap.RecapViewModelFactory
import com.zegrt.rupee.recurring.RecurringScreen
import com.zegrt.rupee.recurring.RecurringUiState
import com.zegrt.rupee.recurring.RecurringViewModel
import com.zegrt.rupee.recurring.RecurringViewModelFactory
import com.zegrt.rupee.settings.SettingsUiState
import com.zegrt.rupee.ui.theme.RupeeTheme

private data class NavTab(val tab: HomeTab, val label: String, val icon: ImageVector)
private val navTabs = listOf(
    NavTab(HomeTab.HOME, "Home", Icons.Default.Home),
    NavTab(HomeTab.INBOX, "Inbox", Icons.Default.Inbox),
    NavTab(HomeTab.TRANSACTIONS, "Txns", Icons.Default.Receipt),
    NavTab(HomeTab.CALENDAR, "Calendar", Icons.Default.CalendarMonth),
    NavTab(HomeTab.SETTINGS, "Settings", Icons.Default.Settings),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RupeeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val app = application as RupeeApplication
                    val versionLabel = buildString {
                        append(BuildConfig.VERSION_NAME)
                        if (BuildConfig.DEBUG) append("-debug")
                    }
                    RupeeApp(
                        homeViewModelFactory = HomeViewModelFactory(
                            repository = app.localFinanceRepository,
                            budgetAlertManager = app.budgetAlertManager,
                            duesAlertManager = app.duesAlertManager,
                        ),
                        onboardingViewModelFactory = OnboardingViewModelFactory(
                            repository = app.localFinanceRepository,
                            preferences = app.onboardingPreferences,
                        ),
                        settingsViewModelFactory = SettingsViewModelFactory(
                            repository = app.localFinanceRepository,
                            appVersion = versionLabel,
                        ),
                        cardsEmisViewModelFactory = CardsEmisViewModelFactory(
                            app.localFinanceRepository,
                        ),
                        calendarViewModelFactory = CalendarViewModelFactory(
                            app.localFinanceRepository,
                        ),
                        budgetsViewModelFactory = BudgetsViewModelFactory(
                            app.localFinanceRepository,
                        ),
                        recurringViewModelFactory = RecurringViewModelFactory(
                            app.localFinanceRepository,
                        ),
                        recapViewModelFactory = RecapViewModelFactory(
                            app.localFinanceRepository,
                        ),
                        debugViewModelFactory = DebugViewModelFactory(app.localFinanceRepository),
                        versionLabel = versionLabel,
                    )
                }
            }
        }
    }
}

@Composable
private fun RupeeApp(
    homeViewModelFactory: HomeViewModelFactory,
    onboardingViewModelFactory: OnboardingViewModelFactory,
    settingsViewModelFactory: SettingsViewModelFactory,
    cardsEmisViewModelFactory: CardsEmisViewModelFactory,
    calendarViewModelFactory: CalendarViewModelFactory,
    budgetsViewModelFactory: BudgetsViewModelFactory,
    recurringViewModelFactory: RecurringViewModelFactory,
    recapViewModelFactory: RecapViewModelFactory,
    debugViewModelFactory: DebugViewModelFactory,
    versionLabel: String,
) {
    val homeViewModel: HomeViewModel = viewModel(factory = homeViewModelFactory)
    val onboardingViewModel: OnboardingViewModel = viewModel(factory = onboardingViewModelFactory)
    val settingsViewModel: SettingsViewModel = viewModel(factory = settingsViewModelFactory)
    val cardsEmisViewModel: CardsEmisViewModel = viewModel(factory = cardsEmisViewModelFactory)
    val calendarViewModel: CalendarViewModel = viewModel(factory = calendarViewModelFactory)
    val budgetsViewModel: BudgetsViewModel = viewModel(factory = budgetsViewModelFactory)
    val recurringViewModel: RecurringViewModel = viewModel(factory = recurringViewModelFactory)
    val recapViewModel: RecapViewModel = viewModel(factory = recapViewModelFactory)
    val debugViewModel: DebugViewModel = viewModel(factory = debugViewModelFactory)
    val homeUiState by homeViewModel.uiState.collectAsState()
    val onboardingUiState by onboardingViewModel.uiState.collectAsState()
    val settingsUiState by settingsViewModel.uiState.collectAsState()
    val cardsEmisUiState by cardsEmisViewModel.uiState.collectAsState()
    val calendarUiState by calendarViewModel.uiState.collectAsState()
    val budgetsUiState by budgetsViewModel.uiState.collectAsState()
    val recurringUiState by recurringViewModel.uiState.collectAsState()
    val recapUiState by recapViewModel.uiState.collectAsState()
    val debugUiState by debugViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val postNotificationsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        settingsViewModel.setPostNotificationsGranted(granted)
    }

    // Auto-prompt once per cold start on Android 13+ when the permission isn't granted.
    // The system shows the OS dialog at most once per app install; subsequent calls
    // no-op silently if the user dismissed it, so users can re-trigger via Settings.
    LaunchedEffect(Unit) {
        val granted = PermissionStateChecker.hasNotificationAccess(context)
        onboardingViewModel.syncPermissionState(notificationGranted = granted)
        settingsViewModel.setNotificationGranted(granted)

        val postGranted = PermissionStateChecker.hasPostNotificationsPermission(context)
        settingsViewModel.setPostNotificationsGranted(postGranted)
        if (!postGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            postNotificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val granted = PermissionStateChecker.hasNotificationAccess(context)
                onboardingViewModel.syncPermissionState(notificationGranted = granted)
                settingsViewModel.setNotificationGranted(granted)
                settingsViewModel.setPostNotificationsGranted(
                    PermissionStateChecker.hasPostNotificationsPermission(context),
                )
                homeViewModel.refreshOnResume()
                calendarViewModel.refreshOnResume()
                recapViewModel.refreshOnResume()
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val openNotificationSettings = {
        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    val requestPostNotifications: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            postNotificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val sendFeedback: () -> Unit = {
        val intent = Intent(Intent.ACTION_SENDTO, "mailto:studioxero.biz@gmail.com".toUri()).apply {
            putExtra(Intent.EXTRA_SUBJECT, "Rupee feedback (v${BuildConfig.VERSION_NAME})")
            putExtra(
                Intent.EXTRA_TEXT,
                "Found a bug or have a suggestion?\n\n— App version: ${BuildConfig.VERSION_NAME}\n— Device: ${Build.MANUFACTURER} ${Build.MODEL}\n— Android: ${Build.VERSION.RELEASE}\n\n",
            )
        }
        runCatching {
            context.startActivity(Intent.createChooser(intent, "Send feedback"))
        }
    }

    when (onboardingUiState.currentStep) {
        OnboardingStep.WELCOME -> WelcomeScreen(onContinue = onboardingViewModel::advanceFromWelcome)
        OnboardingStep.PROFILE -> ProfileScreen(
            uiState = onboardingUiState,
            onDisplayNameChange = onboardingViewModel::updateDisplayName,
            onMonthlyBudgetChange = onboardingViewModel::updateMonthlyBudgetInput,
            onContinue = onboardingViewModel::continueFromProfile,
        )
        OnboardingStep.PERMISSIONS -> PermissionsScreen(
            uiState = onboardingUiState,
            onGrantNotification = openNotificationSettings,
            onContinue = {
                onboardingViewModel.syncPermissionState(
                    notificationGranted = PermissionStateChecker.hasNotificationAccess(context),
                )
                onboardingViewModel.continueFromPermissions()
            },
        )

        OnboardingStep.SETUP -> SetupScreen(
            uiState = onboardingUiState,
            onBankAccountNameChange = onboardingViewModel::updateBankAccountName,
            onBankProviderNameChange = onboardingViewModel::updateBankProviderName,
            onCreditCardNameChange = onboardingViewModel::updateCreditCardName,
            onCreditCardProviderNameChange = onboardingViewModel::updateCreditCardProviderName,
            onToggleCash = onboardingViewModel::toggleCashAccount,
            onCashBalanceChange = onboardingViewModel::updateCashBalanceInput,
            onFinish = onboardingViewModel::finishSetup,
        )

        OnboardingStep.HOME -> RupeeHome(
            uiState = homeUiState,
            settingsState = settingsUiState,
            cardsEmisState = cardsEmisUiState,
            calendarState = calendarUiState,
            budgetsState = budgetsUiState,
            recurringState = recurringUiState,
            recapState = recapUiState,
            debugState = debugUiState,
            versionLabel = versionLabel,
            onSelectTab = homeViewModel::selectTab,
            onSelectReviewRow = homeViewModel::selectReviewRow,
            onSelectMergeTarget = homeViewModel::selectMergeTarget,
            onReviewMerchantDraftChange = homeViewModel::updateReviewMerchantDraft,
            onReviewAmountDraftChange = homeViewModel::updateReviewAmountDraft,
            onReviewCategoryDraftChange = homeViewModel::updateReviewCategoryDraft,
            onToggleAlwaysTrust = homeViewModel::toggleAlwaysTrust,
            onConfirmReviewRow = homeViewModel::confirmReviewRow,
            onDismissReviewRow = homeViewModel::dismissReviewRow,
            onSelectTransaction = homeViewModel::selectTransaction,
            onTransactionMerchantDraftChange = homeViewModel::updateTransactionMerchantDraft,
            onTransactionNotesDraftChange = homeViewModel::updateTransactionNotesDraft,
            onTransactionCategoryDraftChange = homeViewModel::updateTransactionCategoryDraft,
            onTransactionTypeDraftChange = homeViewModel::updateTransactionTypeDraft,
            onSaveTransaction = homeViewModel::saveTransactionEdits,
            onOpenManualEntry = homeViewModel::openManualEntry,
            onCloseManualEntry = homeViewModel::closeManualEntry,
            onUpdateManualEntry = homeViewModel::updateManualEntry,
            onSubmitManualEntry = homeViewModel::submitManualEntry,
            onSettingsNameDraftChange = settingsViewModel::updateNameDraft,
            onSettingsSaveName = settingsViewModel::saveDisplayName,
            onSettingsRemoveTrustRule = settingsViewModel::removeTrustRule,
            onOpenEmiDraft = cardsEmisViewModel::openEmiDraft,
            onCloseEmiDraft = cardsEmisViewModel::closeEmiDraft,
            onUpdateEmiDraft = cardsEmisViewModel::updateEmiDraft,
            onSubmitEmiDraft = cardsEmisViewModel::submitEmiDraft,
            onRemoveEmi = cardsEmisViewModel::removeEmi,
            onOpenCardDueDraft = cardsEmisViewModel::openCardDueDraft,
            onCloseCardDueDraft = cardsEmisViewModel::closeCardDueDraft,
            onUpdateCardDueDraft = cardsEmisViewModel::updateCardDueDraft,
            onSubmitCardDueDraft = cardsEmisViewModel::submitCardDueDraft,
            onSetCardExclude = cardsEmisViewModel::setCardExcludeFromExpenseTotals,
            onCalendarPrev = calendarViewModel::goToPreviousMonth,
            onCalendarNext = calendarViewModel::goToNextMonth,
            onCalendarSelectDate = calendarViewModel::selectDate,
            onCalendarCloseDay = calendarViewModel::closeDayDetail,
            onBudgetsMonthlyDraftChange = budgetsViewModel::updateMonthlyDraft,
            onBudgetsSaveMonthly = budgetsViewModel::saveMonthly,
            onBudgetsCategoryDraftChange = budgetsViewModel::updateCategoryDraft,
            onBudgetsSaveCategory = budgetsViewModel::saveCategoryLimit,
            onRecurringConfirm = recurringViewModel::confirm,
            onRecurringDismiss = recurringViewModel::dismiss,
            onRecurringRemove = recurringViewModel::remove,
            onRecurringRefresh = recurringViewModel::refreshNow,
            onRecapPrev = recapViewModel::goToPreviousMonth,
            onRecapNext = recapViewModel::goToNextMonth,
            onRecapResetToCurrent = recapViewModel::resetToCurrent,
            onOpenNotificationSettings = openNotificationSettings,
            onRequestPostNotifications = requestPostNotifications,
            onSendFeedback = sendFeedback,
            onDebugReset = debugViewModel::resetAllData,
            onDebugSendSample = debugViewModel::sendSample,
            onDebugUpdateParseTitle = debugViewModel::updateParseTitle,
            onDebugUpdateParseBody = debugViewModel::updateParseBody,
            onDebugRunParseTest = { debugViewModel.runParseTest() },
            onCloseTransaction = homeViewModel::closeTransactionDetail,
            onDeleteTransaction = homeViewModel::deleteTransaction,
            onPostMockNotification = { debugViewModel.postMockNotification(context) },
            onDebugUpdateMockTitle = debugViewModel::updateMockTitle,
            onDebugUpdateMockBody = debugViewModel::updateMockBody,
            onDebugLoadMockSample = debugViewModel::loadMockFromSample,
            onDebugWipeRawCapture = debugViewModel::wipeRawCapture,
            onDebugRefreshCrashLog = { debugViewModel.refreshCrashLog(context) },
            onDebugEmailCrashLog = { debugViewModel.emailCrashLog(context) },
            onDebugClearCrashLog = { debugViewModel.clearCrashLog(context) },
            onDebugShareNotificationDumps = { debugViewModel.shareNotificationDumps(context) },
            onDebugClearNotificationDumps = { debugViewModel.clearNotificationDumps(context) },
            onDebugRefreshNotificationDumpSize = { debugViewModel.refreshNotificationDumpSize(context) },
        )
    }
}

@Composable
private fun WelcomeScreen(onContinue: () -> Unit) {
    // Scroll-safe layout: at large font scales / short screens the centered
    // arrangement would push the CTA off-screen. verticalScroll lets the user
    // reach it; Arrangement.Top keeps natural top-anchored flow.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.Start,
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        Text("Rupee", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
        Text(
            text = "Auto-track spending from your phone's transaction signals, then keep budgets, dues, and EMIs in one calm place.",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 12.dp),
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Built for India-first personal finance tracking with low manual effort.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onContinue) { Text("Set up Rupee") }
    }
}

@Composable
private fun PermissionsScreen(
    uiState: OnboardingUiState,
    onGrantNotification: () -> Unit,
    onContinue: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text("Permissions", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text(
            text = "Grant the signals Rupee needs to track spend automatically.",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 12.dp),
        )
        Spacer(modifier = Modifier.height(24.dp))
        PermissionCard(
            state = uiState.notificationPermission,
            primaryLabel = if (uiState.notificationPermission.isGranted) "Granted" else "Enable",
            onPrimaryAction = onGrantNotification,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "The current preview uses notification access only, which keeps install friction lower.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onContinue, enabled = uiState.notificationPermission.isGranted) {
            Text("Continue")
        }
    }
}

@Composable
private fun ProfileScreen(
    uiState: OnboardingUiState,
    onDisplayNameChange: (String) -> Unit,
    onMonthlyBudgetChange: (String) -> Unit,
    onContinue: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text("Quick profile", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text(
            text = "Tell us what to call you and a rough monthly spend target. You can edit both later in Settings.",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 12.dp),
        )
        Spacer(modifier = Modifier.height(24.dp))
        OutlinedTextField(
            value = uiState.profileForm.displayName,
            onValueChange = onDisplayNameChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Your name") },
            singleLine = true,
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = uiState.profileForm.monthlyBudgetInput,
            onValueChange = { v -> onMonthlyBudgetChange(v.filter { it.isDigit() || it == '.' || it == ',' }) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Monthly budget (₹)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        uiState.profileError?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onContinue,
            enabled = !uiState.isSavingProfile,
        ) {
            Text(if (uiState.isSavingProfile) "Saving..." else "Continue")
        }
    }
}

@Composable
private fun PermissionCard(
    state: PermissionCardState,
    primaryLabel: String,
    onPrimaryAction: () -> Unit,
    secondaryLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
) {
    Card(
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(state.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                text = state.description,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = state.statusLabel,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 12.dp),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onPrimaryAction, enabled = !state.isGranted) { Text(primaryLabel) }
                if (secondaryLabel != null && onSecondaryAction != null) {
                    OutlinedButton(onClick = onSecondaryAction) { Text(secondaryLabel) }
                }
            }
        }
    }
}

@Composable
private fun SetupScreen(
    uiState: OnboardingUiState,
    onBankAccountNameChange: (String) -> Unit,
    onBankProviderNameChange: (String) -> Unit,
    onCreditCardNameChange: (String) -> Unit,
    onCreditCardProviderNameChange: (String) -> Unit,
    onToggleCash: () -> Unit,
    onCashBalanceChange: (String) -> Unit,
    onFinish: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text("Initial setup", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text(
            text = "Add the first account types Rupee should manage. You only need one to continue.",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 12.dp),
        )
        Spacer(modifier = Modifier.height(24.dp))
        SetupInputCard(title = "Bank account") {
            OutlinedTextField(
                value = uiState.setupForm.bankAccountName,
                onValueChange = onBankAccountNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Account name") },
                placeholder = { Text("SBI Primary") },
                singleLine = true,
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = uiState.setupForm.bankProviderName,
                onValueChange = onBankProviderNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Bank provider") },
                placeholder = { Text("SBI / Kotak") },
                singleLine = true,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        SetupInputCard(title = "Credit card") {
            OutlinedTextField(
                value = uiState.setupForm.creditCardName,
                onValueChange = onCreditCardNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Card name") },
                placeholder = { Text("ICICI Coral") },
                singleLine = true,
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = uiState.setupForm.creditCardProviderName,
                onValueChange = onCreditCardProviderNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Card provider") },
                placeholder = { Text("ICICI") },
                singleLine = true,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        SetupToggleCard(
            enabled = uiState.setupForm.wantsCashAccount,
            onClick = onToggleCash,
        )
        if (uiState.setupForm.wantsCashAccount) {
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = uiState.setupForm.cashBalanceInput,
                onValueChange = onCashBalanceChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Starting cash balance") },
                placeholder = { Text("500") },
                singleLine = true,
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        uiState.setupError?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }
        Button(onClick = onFinish, enabled = !uiState.isSavingSetup) {
            Text(if (uiState.isSavingSetup) "Saving..." else "Finish setup")
        }
    }
}

@Composable
private fun SetupInputCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
private fun SetupToggleCard(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
            else MaterialTheme.colorScheme.surface,
        ),
        onClick = onClick,
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Cash on hand", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                text = "Keep optional manual cash spending and balances.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = if (enabled) "Selected" else "Tap to include",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun RupeeHome(
    uiState: HomeUiState,
    settingsState: SettingsUiState,
    cardsEmisState: CardsEmisUiState,
    calendarState: CalendarUiState,
    budgetsState: BudgetsUiState,
    recurringState: RecurringUiState,
    recapState: RecapUiState,
    debugState: DebugUiState,
    versionLabel: String,
    onSelectTab: (HomeTab) -> Unit,
    onSelectReviewRow: (String) -> Unit,
    onSelectMergeTarget: (String, String?) -> Unit,
    onReviewMerchantDraftChange: (String, String) -> Unit,
    onReviewAmountDraftChange: (String, String) -> Unit,
    onReviewCategoryDraftChange: (String, String?) -> Unit,
    onToggleAlwaysTrust: (String) -> Unit,
    onConfirmReviewRow: (String, ReviewSource) -> Unit,
    onDismissReviewRow: (String, ReviewSource) -> Unit,
    onSelectTransaction: (String) -> Unit,
    onTransactionMerchantDraftChange: (String, String) -> Unit,
    onTransactionNotesDraftChange: (String, String) -> Unit,
    onTransactionCategoryDraftChange: (String, String?) -> Unit,
    onTransactionTypeDraftChange: (String, com.zegrt.rupee.data.local.entity.CanonicalTransactionType) -> Unit,
    onSaveTransaction: (String) -> Unit,
    onOpenManualEntry: () -> Unit,
    onCloseManualEntry: () -> Unit,
    onUpdateManualEntry: (ManualEntryDraft.() -> ManualEntryDraft) -> Unit,
    onSubmitManualEntry: () -> Unit,
    onSettingsNameDraftChange: (String) -> Unit,
    onSettingsSaveName: () -> Unit,
    onSettingsRemoveTrustRule: (String) -> Unit,
    onOpenEmiDraft: () -> Unit,
    onCloseEmiDraft: () -> Unit,
    onUpdateEmiDraft: (EmiDraft.() -> EmiDraft) -> Unit,
    onSubmitEmiDraft: () -> Unit,
    onRemoveEmi: (String) -> Unit,
    onOpenCardDueDraft: (String) -> Unit,
    onCloseCardDueDraft: () -> Unit,
    onUpdateCardDueDraft: (CardDueDraft.() -> CardDueDraft) -> Unit,
    onSubmitCardDueDraft: () -> Unit,
    onSetCardExclude: (String, Boolean) -> Unit,
    onCalendarPrev: () -> Unit,
    onCalendarNext: () -> Unit,
    onCalendarSelectDate: (java.time.LocalDate) -> Unit,
    onCalendarCloseDay: () -> Unit,
    onBudgetsMonthlyDraftChange: (String) -> Unit,
    onBudgetsSaveMonthly: () -> Unit,
    onBudgetsCategoryDraftChange: (String, String) -> Unit,
    onBudgetsSaveCategory: (String) -> Unit,
    onRecurringConfirm: (String) -> Unit,
    onRecurringDismiss: (String) -> Unit,
    onRecurringRemove: (String) -> Unit,
    onRecurringRefresh: () -> Unit,
    onRecapPrev: () -> Unit,
    onRecapNext: () -> Unit,
    onRecapResetToCurrent: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onRequestPostNotifications: () -> Unit,
    onSendFeedback: () -> Unit,
    onDebugReset: () -> Unit,
    onDebugSendSample: (SampleNotification) -> Unit,
    onDebugUpdateParseTitle: (String) -> Unit,
    onDebugUpdateParseBody: (String) -> Unit,
    onDebugRunParseTest: () -> Unit,
    onCloseTransaction: () -> Unit,
    onDeleteTransaction: (String) -> Unit,
    onPostMockNotification: () -> Unit,
    onDebugUpdateMockTitle: (String) -> Unit,
    onDebugUpdateMockBody: (String) -> Unit,
    onDebugLoadMockSample: (SampleNotification) -> Unit,
    onDebugWipeRawCapture: () -> Unit,
    onDebugRefreshCrashLog: () -> Unit,
    onDebugShareNotificationDumps: () -> Unit,
    onDebugClearNotificationDumps: () -> Unit,
    onDebugRefreshNotificationDumpSize: () -> Unit,
    onDebugEmailCrashLog: () -> Unit,
    onDebugClearCrashLog: () -> Unit,
) {
    var showDebug by remember { mutableStateOf(false) }
    var showTrustRules by remember { mutableStateOf(false) }
    var showCardsEmis by remember { mutableStateOf(false) }
    var showBudgets by remember { mutableStateOf(false) }
    var showRecurring by remember { mutableStateOf(false) }
    var showRecap by remember { mutableStateOf(false) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                navTabs.forEach { item ->
                    NavigationBarItem(
                        selected = uiState.selectedTab == item.tab,
                        onClick = { onSelectTab(item.tab) },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                    )
                }
            }
        },
    ) { paddingValues ->
        androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 24.dp),
            ) {
                when (uiState.selectedTab) {
                    HomeTab.HOME -> HomeSummaryTab(
                        uiState = uiState,
                        budgetsState = budgetsState,
                        versionLabel = versionLabel,
                        onReviewInbox = { onSelectTab(HomeTab.INBOX) },
                        onAddTransaction = onOpenManualEntry,
                    )
                    HomeTab.INBOX -> ReviewTab(
                        uiState = uiState,
                        onSelectReviewRow = onSelectReviewRow,
                        onMerchantChange = onReviewMerchantDraftChange,
                        onAmountChange = onReviewAmountDraftChange,
                        onCategoryChange = onReviewCategoryDraftChange,
                        onToggleAlwaysTrust = onToggleAlwaysTrust,
                        onConfirm = onConfirmReviewRow,
                        onDismiss = onDismissReviewRow,
                        onSelectMergeTarget = onSelectMergeTarget,
                    )
                    HomeTab.TRANSACTIONS -> TransactionsTab(
                        uiState = uiState,
                        onSelectTransaction = onSelectTransaction,
                    )
                    HomeTab.CALENDAR -> CalendarScreen(
                        state = calendarState,
                        onPrev = onCalendarPrev,
                        onNext = onCalendarNext,
                        onSelectDate = onCalendarSelectDate,
                        onCloseDay = onCalendarCloseDay,
                    )
                    HomeTab.SETTINGS -> SettingsScreen(
                        state = settingsState,
                        onNameDraftChange = onSettingsNameDraftChange,
                        onSaveName = onSettingsSaveName,
                        onOpenNotificationSettings = onOpenNotificationSettings,
                        onRequestPostNotifications = onRequestPostNotifications,
                        onOpenTrustRules = { showTrustRules = true },
                        onOpenCardsEmis = { showCardsEmis = true },
                        onOpenBudgets = { showBudgets = true },
                        onOpenRecurring = {
                            onRecurringRefresh()
                            showRecurring = true
                        },
                        onOpenRecap = {
                            onRecapResetToCurrent()
                            showRecap = true
                        },
                        onSendFeedback = onSendFeedback,
                        onOpenDebug = {
                            onDebugRefreshNotificationDumpSize()
                            showDebug = true
                        },
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            // Debug pill only in debug builds. Release-build access is via
            // Settings → About → "Open debug tools" so the affordance still exists
            // for dev work without surfacing it in user-facing flows.
            if (BuildConfig.DEBUG) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.85f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(20.dp)
                        .clickable {
                            onDebugRefreshNotificationDumpSize()
                            showDebug = true
                        },
                ) {
                    Text(
                        "Debug",
                        color = MaterialTheme.colorScheme.onTertiary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }

    if (uiState.manualEntry.isOpen) {
        ManualEntrySheet(
            draft = uiState.manualEntry,
            categories = uiState.categories,
            onClose = onCloseManualEntry,
            onUpdate = onUpdateManualEntry,
            onSubmit = onSubmitManualEntry,
        )
    }

    val selectedTxn = uiState.selectedTransactionId
        ?.let { id -> uiState.recentTransactions.firstOrNull { it.id == id } }
    if (selectedTxn != null && uiState.selectedTab == HomeTab.TRANSACTIONS) {
        TransactionDetailSheet(
            row = selectedTxn,
            categories = uiState.categories,
            onClose = onCloseTransaction,
            onMerchantChange = { onTransactionMerchantDraftChange(selectedTxn.id, it) },
            onNotesChange = { onTransactionNotesDraftChange(selectedTxn.id, it) },
            onCategoryChange = { onTransactionCategoryDraftChange(selectedTxn.id, it) },
            onTypeChange = { onTransactionTypeDraftChange(selectedTxn.id, it) },
            onSave = { onSaveTransaction(selectedTxn.id) },
            onDelete = { onDeleteTransaction(selectedTxn.id) },
        )
    }

    if (showTrustRules) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { if (showTrustRules) showTrustRules = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 24.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Trusted merchants", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        androidx.compose.material3.TextButton(onClick = { if (showTrustRules) showTrustRules = false }) { Text("Close") }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    TrustRulesScreen(
                        rules = settingsState.trustRules,
                        onRemove = onSettingsRemoveTrustRule,
                    )
                }
            }
        }
    }

    if (showCardsEmis) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { if (showCardsEmis) showCardsEmis = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 24.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Cards & EMIs", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        androidx.compose.material3.TextButton(onClick = { if (showCardsEmis) showCardsEmis = false }) { Text("Close") }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    CardsEmisScreen(
                        state = cardsEmisState,
                        onAddEmi = onOpenEmiDraft,
                        onRemoveEmi = onRemoveEmi,
                        onSetCardDue = onOpenCardDueDraft,
                        onSetCardExclude = onSetCardExclude,
                    )
                }
            }
        }
        if (cardsEmisState.emiDraft.isOpen) {
            EmiDraftSheet(
                draft = cardsEmisState.emiDraft,
                onClose = onCloseEmiDraft,
                onUpdate = onUpdateEmiDraft,
                onSubmit = onSubmitEmiDraft,
            )
        }
        if (cardsEmisState.cardDueDraft.isOpen) {
            CardDueDraftSheet(
                draft = cardsEmisState.cardDueDraft,
                onClose = onCloseCardDueDraft,
                onUpdate = onUpdateCardDueDraft,
                onSubmit = onSubmitCardDueDraft,
            )
        }
    }

    if (showRecap) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { if (showRecap) showRecap = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 24.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Monthly recap", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        androidx.compose.material3.TextButton(onClick = { if (showRecap) showRecap = false }) { Text("Close") }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    RecapScreen(
                        state = recapState,
                        onPrev = onRecapPrev,
                        onNext = onRecapNext,
                    )
                }
            }
        }
    }

    if (showRecurring) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { if (showRecurring) showRecurring = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 24.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Recurring", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        androidx.compose.material3.TextButton(onClick = { if (showRecurring) showRecurring = false }) { Text("Close") }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    RecurringScreen(
                        state = recurringState,
                        onConfirm = onRecurringConfirm,
                        onDismiss = onRecurringDismiss,
                        onRemove = onRecurringRemove,
                        onRefresh = onRecurringRefresh,
                    )
                }
            }
        }
    }

    if (showBudgets) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { if (showBudgets) showBudgets = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 24.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Budgets", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        androidx.compose.material3.TextButton(onClick = { if (showBudgets) showBudgets = false }) { Text("Close") }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    BudgetsScreen(
                        state = budgetsState,
                        onMonthlyDraftChange = onBudgetsMonthlyDraftChange,
                        onSaveMonthly = onBudgetsSaveMonthly,
                        onCategoryDraftChange = onBudgetsCategoryDraftChange,
                        onSaveCategory = onBudgetsSaveCategory,
                    )
                }
            }
        }
    }

    if (showDebug) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { if (showDebug) showDebug = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 24.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Debug", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        androidx.compose.material3.TextButton(onClick = { if (showDebug) showDebug = false }) { Text("Close") }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    DebugScreen(
                        state = debugState,
                        onReset = onDebugReset,
                        onSendSample = onDebugSendSample,
                        onUpdateParseTitle = onDebugUpdateParseTitle,
                        onUpdateParseBody = onDebugUpdateParseBody,
                        onRunParseTest = onDebugRunParseTest,
                        onPostMockNotification = onPostMockNotification,
                        onUpdateMockTitle = onDebugUpdateMockTitle,
                        onUpdateMockBody = onDebugUpdateMockBody,
                        onLoadMockSample = onDebugLoadMockSample,
                        onWipeRawCapture = onDebugWipeRawCapture,
                        onViewCrashLog = onDebugRefreshCrashLog,
                        onEmailCrashLog = onDebugEmailCrashLog,
                        onClearCrashLog = onDebugClearCrashLog,
                        crashLogPreview = debugState.crashLogPreview,
                        onShareNotificationDumps = onDebugShareNotificationDumps,
                        onClearNotificationDumps = onDebugClearNotificationDumps,
                        notificationDumpSize = debugState.notificationDumpSize,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeSummaryTab(
    uiState: HomeUiState,
    budgetsState: BudgetsUiState,
    versionLabel: String = "",
    onReviewInbox: () -> Unit,
    onAddTransaction: () -> Unit,
) {
    val dashboard = uiState.dashboard
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        DashboardGreeting(
            greeting = dashboard.greeting,
            monthLabel = dashboard.monthLabel,
            isSeeding = uiState.isSeeding,
            versionLabel = versionLabel,
        )
        HeroBudgetCard(dashboard = dashboard)
        WeeklySpendCard(dashboard = dashboard)
        BucketProgressCard(rows = budgetsState.categoryRows)
        UpcomingDuesCard(dues = dashboard.upcomingDues)
        QuickActionsRow(
            pendingReviewCount = dashboard.pendingReviewCount,
            onReviewInbox = onReviewInbox,
            onAddTransaction = onAddTransaction,
        )
        RecentActivityCard(
            recents = dashboard.recentTransactions,
            hasAny = dashboard.hasAnyTransactions,
        )
    }
}

@Composable
private fun UpcomingDuesCard(dues: List<HomeDueRow>) {
    if (dues.isEmpty()) return
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Text("Upcoming dues", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Next 14 days",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(modifier = Modifier.height(12.dp))
            dues.forEach { due ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${when (due.kind) {
                                HomeDueKind.CARD -> "Card • "
                                HomeDueKind.EMI -> "EMI • "
                                HomeDueKind.RECURRING -> "Recurring • "
                            }}${due.title}",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = due.dueLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (due.isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(due.amountLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun DashboardGreeting(
    greeting: String,
    monthLabel: String,
    isSeeding: Boolean,
    versionLabel: String = "",
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // weight(1f) gives the greeting all remaining horizontal space;
            // maxLines = 1 + Ellipsis bounds the Row's height so the monthLabel
            // below sits at a predictable distance regardless of name length
            // or font scale. Previously SpaceBetween let the greeting wrap on
            // long names / large font, creating the "weird gap" above the
            // month label.
            Text(
                greeting,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (versionLabel.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text(
                        text = "v$versionLabel",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }
        Text(
            text = if (isSeeding) "Setting up your local store..." else monthLabel,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun HeroBudgetCard(dashboard: HomeDashboard) {
    val accent = when {
        dashboard.isOverBudget -> MaterialTheme.colorScheme.error
        dashboard.isNearLimit -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = if (dashboard.hasMonthlyBudget) "Remaining this month" else "Monthly budget",
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (dashboard.hasMonthlyBudget) dashboard.monthlyRemainingLabel else dashboard.monthlyBudgetLabel,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = accent,
            )
            if (dashboard.hasMonthlyBudget) {
                Spacer(modifier = Modifier.height(16.dp))
                LinearProgressIndicator(
                    progress = { dashboard.monthlyProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = accent,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text("Spent", style = MaterialTheme.typography.labelMedium)
                        Text(
                            dashboard.monthlySpentLabel,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Budget", style = MaterialTheme.typography.labelMedium)
                        Text(
                            dashboard.monthlyBudgetLabel,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                if (dashboard.isOverBudget) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Over budget — review spending in Inbox.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else if (dashboard.isNearLimit) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Approaching the monthly limit.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Add a monthly target to see remaining and progress.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun BucketProgressCard(rows: List<CategoryBudgetRow>) {
    val limitedRows = rows.filter { it.hasLimit }
    if (limitedRows.isEmpty()) return
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "Category budgets",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(16.dp))
            limitedRows.forEachIndexed { index, row ->
                val accent = when {
                    row.isOverLimit -> MaterialTheme.colorScheme.error
                    row.progress >= 0.8f -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.primary
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(row.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = buildString {
                            append(row.spentLabel)
                            row.limitLabel?.let { append(" $it") }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (row.isOverLimit) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { row.progress },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = accent,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                if (index < limitedRows.lastIndex) Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun WeeklySpendCard(dashboard: HomeDashboard) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("This week", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    dashboard.weeklySpentLabel,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(dashboard.weekRangeLabel, style = MaterialTheme.typography.bodySmall)
                dashboard.monthlyIncomeLabel?.let { incomeLabel ->
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Received this month $incomeLabel",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
            ) {
                Text(
                    "Spent",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun QuickActionsRow(
    pendingReviewCount: Int,
    onReviewInbox: () -> Unit,
    onAddTransaction: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(
            onClick = onReviewInbox,
            modifier = Modifier.weight(1f),
            enabled = pendingReviewCount > 0,
        ) {
            Text(
                if (pendingReviewCount > 0) "Review ($pendingReviewCount)" else "Inbox clear",
            )
        }
        OutlinedButton(
            onClick = onAddTransaction,
            modifier = Modifier.weight(1f),
        ) {
            Text("Add transaction")
        }
    }
}

@Composable
private fun RecentActivityCard(
    recents: List<HomeRecentRow>,
    hasAny: Boolean,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Recent activity", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(12.dp))
            if (!hasAny) {
                Text(
                    "Rupee will start filling this in once it sees transaction notifications. Make sure notification access is granted.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else if (recents.isEmpty()) {
                Text("No transactions in the last few entries.", style = MaterialTheme.typography.bodyMedium)
            } else {
                recents.forEachIndexed { index, row ->
                    RecentRow(row = row)
                    if (index < recents.lastIndex) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentRow(row: HomeRecentRow) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                row.merchant,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(row.subline, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            if (row.isSuggested) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f),
                ) {
                    Text(
                        "Suggested",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = row.amountLabel,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (row.isIncome) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ManualEntrySheet(
    draft: ManualEntryDraft,
    categories: List<CategoryOption>,
    onClose: () -> Unit,
    onUpdate: (ManualEntryDraft.() -> ManualEntryDraft) -> Unit,
    onSubmit: () -> Unit,
) {
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onClose,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Add transaction", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                ManualEntryType.entries.forEachIndexed { index, t ->
                    SegmentedButton(
                        selected = draft.type == t,
                        onClick = { onUpdate { copy(type = t) } },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = ManualEntryType.entries.size),
                        label = { Text(if (t == ManualEntryType.INCOME) "Income" else "Expense") },
                    )
                }
            }
            OutlinedTextField(
                value = draft.merchant,
                onValueChange = { v -> onUpdate { copy(merchant = v) } },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(if (draft.type == ManualEntryType.INCOME) "Source / payer" else "Merchant / payee") },
                singleLine = true,
            )
            OutlinedTextField(
                value = draft.amountRupees,
                onValueChange = { v -> onUpdate { copy(amountRupees = v.filter { it.isDigit() || it == '.' }) } },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Amount (₹)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            Text("Mode", style = MaterialTheme.typography.labelMedium)
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(Mode.UPI, Mode.CREDIT_CARD, Mode.DEBIT_CARD, Mode.CASH, Mode.BANK_TRANSFER, Mode.OTHER).forEach { m ->
                    FilterChip(
                        selected = draft.mode == m,
                        onClick = { onUpdate { copy(mode = m) } },
                        label = { Text(m.name.replace('_', ' ')) },
                    )
                }
            }
            CategoryChipRow(
                selectedId = draft.categoryId,
                categories = categories,
                onSelect = { id -> onUpdate { copy(categoryId = id) } },
            )
            OutlinedTextField(
                value = draft.notes,
                onValueChange = { v -> onUpdate { copy(notes = v) } },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Notes (optional)") },
            )
            draft.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onSubmit,
                    enabled = !draft.isSaving,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (draft.isSaving) "Saving..." else "Save")
                }
                OutlinedButton(onClick = onClose, modifier = Modifier.weight(1f)) {
                    Text("Cancel")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun MergeTransactionPickerSheet(
    transactions: List<HomeTransactionRow>,
    onSelect: (HomeTransactionRow) -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Text("Merge with transaction", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("Attach this signal to an existing transaction instead of creating a new one.", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(16.dp))
            if (transactions.isEmpty()) {
                Text("No transactions to merge with.", style = MaterialTheme.typography.bodyMedium)
            } else {
                transactions.forEach { txn ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        onClick = { onSelect(txn) },
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(txn.headline, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text(txn.subline, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp))
                            }
                            Text(txn.amountLabel, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ReviewTab(
    uiState: HomeUiState,
    onSelectReviewRow: (String) -> Unit,
    onMerchantChange: (String, String) -> Unit,
    onAmountChange: (String, String) -> Unit,
    onCategoryChange: (String, String?) -> Unit,
    onToggleAlwaysTrust: (String) -> Unit,
    onConfirm: (String, ReviewSource) -> Unit,
    onDismiss: (String, ReviewSource) -> Unit,
    onSelectMergeTarget: (String, String?) -> Unit,
) {
    var mergePickerForId by remember { mutableStateOf<String?>(null) }
    InspectionSection(
        title = "Review",
        hasItems = uiState.reviewRows.isNotEmpty(),
        emptyLabel = "Nothing to review.",
    ) {
        uiState.reviewRows.forEach { row ->
            ReviewRowCard(
                row = row,
                selected = row.id == uiState.selectedReviewRowId,
                categories = uiState.categories,
                onSelect = { onSelectReviewRow(row.id) },
                onMerchantChange = { onMerchantChange(row.id, it) },
                onAmountChange = { onAmountChange(row.id, it) },
                onCategoryChange = { onCategoryChange(row.id, it) },
                onToggleAlwaysTrust = { onToggleAlwaysTrust(row.id) },
                onConfirm = { onConfirm(row.id, row.source) },
                onDismiss = { onDismiss(row.id, row.source) },
                onClearMergeTarget = { onSelectMergeTarget(row.id, null) },
                onOpenMergePicker = { mergePickerForId = row.id },
                hasMergeTarget = row.mergeTargetId != null,
                mergeTargetMerchant = row.mergeTargetId?.let { tid -> uiState.recentTransactions.firstOrNull { it.id == tid }?.headline },
            )
        }
    }
    if (mergePickerForId != null) {
        MergeTransactionPickerSheet(
            transactions = uiState.recentTransactions,
            onSelect = { txn ->
                onSelectMergeTarget(mergePickerForId!!, txn.id)
                mergePickerForId = null
            },
            onDismiss = { if (mergePickerForId != null) mergePickerForId = null },
        )
    }
}

@Composable
private fun TransactionsTab(
    uiState: HomeUiState,
    onSelectTransaction: (String) -> Unit,
) {
    InspectionSection(
        title = "Transactions",
        hasItems = uiState.recentTransactions.isNotEmpty(),
        emptyLabel = "No canonical transactions yet.",
    ) {
        uiState.recentTransactions.forEach { row ->
            TransactionRow(
                row = row,
                onSelect = { onSelectTransaction(row.id) },
            )
        }
    }
}

@Composable
private fun ReviewRowCard(
    row: HomeReviewRow,
    selected: Boolean,
    categories: List<CategoryOption>,
    onSelect: () -> Unit,
    onMerchantChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onCategoryChange: (String?) -> Unit,
    onToggleAlwaysTrust: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onClearMergeTarget: () -> Unit = {},
    onOpenMergePicker: () -> Unit = {},
    hasMergeTarget: Boolean = false,
    mergeTargetMerchant: String? = null,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        onClick = onSelect,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        row.merchant,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(row.subline, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(row.amountLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (row.source == ReviewSource.SUGGESTED)
                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
                else MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Text(
                    row.reasonLabel,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            if (selected) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = row.merchantDraft,
                    onValueChange = onMerchantChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Merchant / payee") },
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = row.amountDraftRupees,
                    onValueChange = onAmountChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Amount (₹)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Spacer(modifier = Modifier.height(8.dp))
                CategoryDropdown(
                    selectedId = row.categoryIdDraft,
                    categories = categories,
                    onSelect = onCategoryChange,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onToggleAlwaysTrust)
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Always trust ${row.merchantDraft.ifBlank { row.merchant }}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "Future notifications from this merchant skip the Inbox and land as Confirmed.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    androidx.compose.material3.Switch(
                        checked = row.alwaysTrust,
                        onCheckedChange = { onToggleAlwaysTrust() },
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (hasMergeTarget && mergeTargetMerchant != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "→ $mergeTargetMerchant",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            androidx.compose.material3.TextButton(onClick = onClearMergeTarget) { Text("✕") }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onConfirm) { Text(if (hasMergeTarget) "Merge" else "Confirm") }
                    OutlinedButton(onClick = onDismiss) { Text("Dismiss") }
                    if (!hasMergeTarget && row.source == ReviewSource.INBOX) {
                        androidx.compose.material3.TextButton(onClick = onOpenMergePicker) { Text("Merge with existing") }
                    }
                }
            }
        }
    }
    Spacer(modifier = Modifier.height(12.dp))
}

@Composable
private fun CategoryDropdown(
    selectedId: String?,
    categories: List<CategoryOption>,
    onSelect: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = categories.firstOrNull { it.id == selectedId }?.name ?: "None"
    androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            enabled = false,
            label = { Text("Category") },
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true },
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                disabledBorderColor = MaterialTheme.colorScheme.outlineVariant,
                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
        androidx.compose.material3.DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            androidx.compose.material3.DropdownMenuItem(
                text = { Text("None") },
                onClick = {
                    onSelect(null)
                    expanded = false
                },
            )
            categories.forEach { option ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(option.name) },
                    onClick = {
                        onSelect(option.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun CategoryChipRow(
    selectedId: String?,
    categories: List<CategoryOption>,
    onSelect: (String?) -> Unit,
) {
    if (categories.isEmpty()) return
    Column {
        Text("Category", style = MaterialTheme.typography.labelMedium)
        Spacer(modifier = Modifier.height(6.dp))
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            FilterChip(
                selected = selectedId == null,
                onClick = { onSelect(null) },
                label = { Text("None") },
            )
            categories.forEach { option ->
                FilterChip(
                    selected = selectedId == option.id,
                    onClick = { onSelect(option.id) },
                    label = { Text(option.name) },
                )
            }
        }
    }
}

@Composable
private fun TransactionRow(
    row: HomeTransactionRow,
    onSelect: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        onClick = onSelect,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    row.headline,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(row.subline, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = row.amountLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (row.isIncome) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
    Spacer(modifier = Modifier.height(12.dp))
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun TransactionDetailSheet(
    row: HomeTransactionRow,
    categories: List<CategoryOption>,
    onClose: () -> Unit,
    onMerchantChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onCategoryChange: (String?) -> Unit,
    onTypeChange: (com.zegrt.rupee.data.local.entity.CanonicalTransactionType) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onClose) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(row.amountLabel, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Text(row.headline, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(row.subline, style = MaterialTheme.typography.bodyMedium)
            if (!editing) {
                row.categoryLabel?.let {
                    Text("Category", style = MaterialTheme.typography.labelMedium)
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
                if (row.notes.isNotBlank()) {
                    Text("Notes", style = MaterialTheme.typography.labelMedium)
                    Text(row.notes, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { editing = true }, modifier = Modifier.weight(1f)) { Text("Edit") }
                    OutlinedButton(
                        onClick = { confirmDelete = true },
                        modifier = Modifier.weight(1f),
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Text("Delete")
                    }
                }
            } else {
                // Type toggle first — flipping a mis-classified P2P inflow from
                // Expense → Income is the most common edit motion, and the choice
                // affects how the merchant field reads ("source / payer" vs
                // "merchant"). Mirror the manual-entry sheet's pattern.
                val isIncome = row.typeDraft == com.zegrt.rupee.data.local.entity.CanonicalTransactionType.INCOME
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = !isIncome,
                        onClick = {
                            onTypeChange(com.zegrt.rupee.data.local.entity.CanonicalTransactionType.EXPENSE)
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        label = { Text("Expense") },
                    )
                    SegmentedButton(
                        selected = isIncome,
                        onClick = {
                            onTypeChange(com.zegrt.rupee.data.local.entity.CanonicalTransactionType.INCOME)
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        label = { Text("Income") },
                    )
                }
                OutlinedTextField(
                    value = row.merchantDraft,
                    onValueChange = onMerchantChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (isIncome) "Source / payer" else "Merchant") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = row.notesDraft,
                    onValueChange = onNotesChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Notes") },
                    minLines = 2,
                )
                CategoryDropdown(
                    selectedId = row.categoryIdDraft,
                    categories = categories,
                    onSelect = onCategoryChange,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = {
                        onSave()
                        editing = false
                    }, modifier = Modifier.weight(1f)) { Text("Save") }
                    OutlinedButton(onClick = { editing = false }, modifier = Modifier.weight(1f)) { Text("Cancel") }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
    if (confirmDelete) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { if (confirmDelete) confirmDelete = false },
            title = { Text("Delete this transaction?") },
            text = { Text("It will be hidden from the dashboard and totals. There is no undo from the UI.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    if (confirmDelete) confirmDelete = false
                    onDelete()
                }) { Text("Delete") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { if (confirmDelete) confirmDelete = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun InspectionSection(
    title: String,
    hasItems: Boolean,
    emptyLabel: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(16.dp))
            if (hasItems) {
                content()
            } else {
                Text(emptyLabel, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun WelcomeScreenPreview() {
    RupeeTheme { WelcomeScreen(onContinue = {}) }
}

@Preview(showBackground = true, name = "Profile — empty")
@Preview(showBackground = true, name = "Profile — large font", fontScale = 1.5f)
@Preview(showBackground = true, name = "Profile — compact width", widthDp = 360)
@Composable
fun ProfileScreenPreview() {
    RupeeTheme {
        ProfileScreen(
            uiState = OnboardingUiState(
                currentStep = OnboardingStep.PROFILE,
                profileForm = ProfileFormState(
                    displayName = "",
                    monthlyBudgetInput = "",
                ),
            ),
            onDisplayNameChange = {},
            onMonthlyBudgetChange = {},
            onContinue = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PermissionsScreenPreview() {
    RupeeTheme {
        PermissionsScreen(
            uiState = OnboardingUiState(
                currentStep = OnboardingStep.PERMISSIONS,
                notificationPermission = PermissionCardState(
                    title = "Notification access",
                    description = "Lets Rupee read transaction alerts from supported apps.",
                    statusLabel = "Granted",
                    isGranted = true,
                ),
            ),
            onGrantNotification = {},
            onContinue = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
fun SetupScreenPreview() {
    RupeeTheme {
        SetupScreen(
            uiState = OnboardingUiState(currentStep = OnboardingStep.SETUP),
            onBankAccountNameChange = {},
            onBankProviderNameChange = {},
            onCreditCardNameChange = {},
            onCreditCardProviderNameChange = {},
            onToggleCash = {},
            onCashBalanceChange = {},
            onFinish = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    RupeeTheme {
        HomeSummaryTab(
            uiState = HomeUiState(
                userName = "Cyril",
                isSeeding = false,
            ),
            budgetsState = BudgetsUiState(),
            onReviewInbox = {},
            onAddTransaction = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ManualEntrySheetPreview() {
    RupeeTheme {
        ManualEntrySheet(
            draft = ManualEntryDraft(isOpen = true),
            categories = listOf(
                CategoryOption("1", "Food"),
                CategoryOption("2", "Transport"),
            ),
            onClose = {},
            onUpdate = {},
            onSubmit = {},
        )
    }
}
