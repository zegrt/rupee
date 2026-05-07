package com.zegrt.rupee

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zegrt.rupee.home.HomeDashboard
import com.zegrt.rupee.home.HomeInboxRow
import com.zegrt.rupee.home.HomeRecentRow
import com.zegrt.rupee.home.HomeUiState
import com.zegrt.rupee.home.HomeTab
import com.zegrt.rupee.home.HomeTransactionRow
import com.zegrt.rupee.home.HomeViewModel
import com.zegrt.rupee.home.HomeViewModelFactory
import com.zegrt.rupee.onboarding.OnboardingStep
import com.zegrt.rupee.onboarding.OnboardingUiState
import com.zegrt.rupee.onboarding.OnboardingViewModel
import com.zegrt.rupee.onboarding.OnboardingViewModelFactory
import com.zegrt.rupee.onboarding.PermissionCardState
import com.zegrt.rupee.onboarding.PermissionStateChecker
import com.zegrt.rupee.ui.theme.RupeeTheme

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
                    RupeeApp(
                        homeViewModelFactory = HomeViewModelFactory(app.localFinanceRepository),
                        onboardingViewModelFactory = OnboardingViewModelFactory(
                            repository = app.localFinanceRepository,
                            preferences = app.onboardingPreferences,
                        ),
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
) {
    val homeViewModel: HomeViewModel = viewModel(factory = homeViewModelFactory)
    val onboardingViewModel: OnboardingViewModel = viewModel(factory = onboardingViewModelFactory)
    val homeUiState by homeViewModel.uiState.collectAsState()
    val onboardingUiState by onboardingViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        onboardingViewModel.syncPermissionState(
            notificationGranted = PermissionStateChecker.hasNotificationAccess(context),
        )
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                onboardingViewModel.syncPermissionState(
                    notificationGranted = PermissionStateChecker.hasNotificationAccess(context),
                )
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    when (onboardingUiState.currentStep) {
        OnboardingStep.WELCOME -> WelcomeScreen(onContinue = onboardingViewModel::advanceFromWelcome)
        OnboardingStep.PERMISSIONS -> PermissionsScreen(
            uiState = onboardingUiState,
            onGrantNotification = {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            },
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
            onSelectTab = homeViewModel::selectTab,
            onSelectInboxItem = homeViewModel::selectInboxItem,
            onInboxMerchantDraftChange = homeViewModel::updateInboxMerchantDraft,
            onConfirmInboxItem = homeViewModel::confirmInboxItem,
            onDismissInboxItem = homeViewModel::dismissInboxItem,
            onSelectTransaction = homeViewModel::selectTransaction,
            onTransactionMerchantDraftChange = homeViewModel::updateTransactionMerchantDraft,
            onTransactionNotesDraftChange = homeViewModel::updateTransactionNotesDraft,
            onSaveTransaction = homeViewModel::saveTransactionEdits,
        )
    }
}

@Composable
private fun WelcomeScreen(onContinue: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start,
    ) {
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
        Card(
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "SMS reading",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = uiState.smsLaterMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    text = "Later build",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
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
            title = "Cash on hand",
            description = "Keep optional manual cash spending and balances.",
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
    title: String,
    description: String,
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
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                text = description,
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
    onSelectTab: (HomeTab) -> Unit,
    onSelectInboxItem: (String) -> Unit,
    onInboxMerchantDraftChange: (String, String) -> Unit,
    onConfirmInboxItem: (String) -> Unit,
    onDismissInboxItem: (String) -> Unit,
    onSelectTransaction: (String) -> Unit,
    onTransactionMerchantDraftChange: (String, String) -> Unit,
    onTransactionNotesDraftChange: (String, String) -> Unit,
    onSaveTransaction: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text("Rupee", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
        Text(
            text = "Pipeline view for the India-first personal finance tracker.",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 12.dp),
        )
        Spacer(modifier = Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HomeTabChip(
                label = "Home",
                selected = uiState.selectedTab == HomeTab.HOME,
                onClick = { onSelectTab(HomeTab.HOME) },
            )
            HomeTabChip(
                label = "Inbox",
                selected = uiState.selectedTab == HomeTab.INBOX,
                onClick = { onSelectTab(HomeTab.INBOX) },
            )
            HomeTabChip(
                label = "Transactions",
                selected = uiState.selectedTab == HomeTab.TRANSACTIONS,
                onClick = { onSelectTab(HomeTab.TRANSACTIONS) },
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        when (uiState.selectedTab) {
            HomeTab.HOME -> HomeSummaryTab(
                uiState = uiState,
                onReviewInbox = { onSelectTab(HomeTab.INBOX) },
            )
            HomeTab.INBOX -> InboxTab(
                uiState = uiState,
                onSelectInboxItem = onSelectInboxItem,
                onInboxMerchantDraftChange = onInboxMerchantDraftChange,
                onConfirmInboxItem = onConfirmInboxItem,
                onDismissInboxItem = onDismissInboxItem,
            )
            HomeTab.TRANSACTIONS -> TransactionsTab(
                uiState = uiState,
                onSelectTransaction = onSelectTransaction,
                onTransactionMerchantDraftChange = onTransactionMerchantDraftChange,
                onTransactionNotesDraftChange = onTransactionNotesDraftChange,
                onSaveTransaction = onSaveTransaction,
            )
        }
    }
}

@Composable
private fun HomeSummaryTab(
    uiState: HomeUiState,
    onReviewInbox: () -> Unit,
) {
    val dashboard = uiState.dashboard
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        DashboardGreeting(
            greeting = dashboard.greeting,
            monthLabel = dashboard.monthLabel,
            isSeeding = uiState.isSeeding,
        )
        HeroBudgetCard(dashboard = dashboard)
        WeeklySpendCard(dashboard = dashboard)
        QuickActionsRow(
            pendingInboxCount = dashboard.pendingInboxCount,
            onReviewInbox = onReviewInbox,
        )
        RecentActivityCard(
            recents = dashboard.recentTransactions,
            hasAny = dashboard.hasAnyTransactions,
        )
    }
}

@Composable
private fun DashboardGreeting(
    greeting: String,
    monthLabel: String,
    isSeeding: Boolean,
) {
    Column {
        Text(greeting, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
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
    pendingInboxCount: Int,
    onReviewInbox: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(
            onClick = onReviewInbox,
            modifier = Modifier.weight(1f),
            enabled = pendingInboxCount > 0,
        ) {
            Text(
                if (pendingInboxCount > 0) "Review Inbox ($pendingInboxCount)" else "Inbox clear",
            )
        }
        OutlinedButton(
            onClick = { /* manual entry — placeholder until Milestone 6 wraps */ },
            modifier = Modifier.weight(1f),
            enabled = false,
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
            Text(row.merchant, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
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
        )
    }
}

@Composable
private fun InboxTab(
    uiState: HomeUiState,
    onSelectInboxItem: (String) -> Unit,
    onInboxMerchantDraftChange: (String, String) -> Unit,
    onConfirmInboxItem: (String) -> Unit,
    onDismissInboxItem: (String) -> Unit,
) {
    InspectionSection(
        title = "Inbox review",
        hasItems = uiState.inboxItems.isNotEmpty(),
        emptyLabel = "Inbox is clear.",
    ) {
        uiState.inboxItems.forEach { row ->
            InboxRow(
                row = row,
                selected = row.id == uiState.selectedInboxItemId,
                onSelect = { onSelectInboxItem(row.id) },
                onMerchantChange = { onInboxMerchantDraftChange(row.id, it) },
                onConfirm = { onConfirmInboxItem(row.id) },
                onDismiss = { onDismissInboxItem(row.id) },
            )
        }
    }
}

@Composable
private fun TransactionsTab(
    uiState: HomeUiState,
    onSelectTransaction: (String) -> Unit,
    onTransactionMerchantDraftChange: (String, String) -> Unit,
    onTransactionNotesDraftChange: (String, String) -> Unit,
    onSaveTransaction: (String) -> Unit,
) {
    InspectionSection(
        title = "Transactions",
        hasItems = uiState.recentTransactions.isNotEmpty(),
        emptyLabel = "No canonical transactions yet.",
    ) {
        uiState.recentTransactions.forEach { row ->
            TransactionRow(
                row = row,
                selected = row.id == uiState.selectedTransactionId,
                onSelect = { onSelectTransaction(row.id) },
                onMerchantChange = { onTransactionMerchantDraftChange(row.id, it) },
                onNotesChange = { onTransactionNotesDraftChange(row.id, it) },
                onSave = { onSaveTransaction(row.id) },
            )
        }
    }
}

@Composable
private fun HomeTabChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
    )
}

@Composable
private fun InboxRow(
    row: HomeInboxRow,
    selected: Boolean,
    onSelect: () -> Unit,
    onMerchantChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
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
            Text(row.headline, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(row.subline, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            Text(row.reasonLabel, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
            if (selected) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = row.merchantDraft,
                    onValueChange = onMerchantChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Merchant / payee") },
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onConfirm) { Text("Confirm") }
                    OutlinedButton(onClick = onDismiss) { Text("Dismiss") }
                }
            }
        }
    }
    Spacer(modifier = Modifier.height(12.dp))
}

@Composable
private fun TransactionRow(
    row: HomeTransactionRow,
    selected: Boolean,
    onSelect: () -> Unit,
    onMerchantChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onSave: () -> Unit,
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
                    Text(row.headline, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(row.subline, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = row.amountLabel,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(12.dp),
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
            if (selected) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = row.merchantDraft,
                    onValueChange = onMerchantChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Merchant") },
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = row.notesDraft,
                    onValueChange = onNotesChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Notes") },
                    minLines = 2,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = onSave) { Text("Save changes") }
            }
        }
    }
    Spacer(modifier = Modifier.height(12.dp))
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

@Composable
private fun InspectionRow(
    headline: String,
    subline: String,
    trailing: String,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(headline, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    text = subline,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Text(
                text = trailing,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .background(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(12.dp),
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Preview(showBackground = true)
@Composable
private fun WelcomeScreenPreview() {
    RupeeTheme { WelcomeScreen(onContinue = {}) }
}

@Preview(showBackground = true)
@Composable
private fun PermissionsScreenPreview() {
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
private fun SetupScreenPreview() {
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
private fun HomeScreenPreview() {
    RupeeTheme {
        RupeeHome(
            uiState = HomeUiState(
                userName = "Cyril",
                accountCount = 1,
                cardCount = 1,
                categoryCount = 11,
                bucketCount = 7,
                recentTransactionCount = 0,
                isSeeding = false,
            ),
            onSelectTab = {},
            onSelectInboxItem = {},
            onInboxMerchantDraftChange = { _, _ -> },
            onConfirmInboxItem = {},
            onDismissInboxItem = {},
            onSelectTransaction = {},
            onTransactionMerchantDraftChange = { _, _ -> },
            onTransactionNotesDraftChange = { _, _ -> },
            onSaveTransaction = {},
        )
    }
}
