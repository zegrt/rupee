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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
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
import com.zegrt.rupee.home.HomeUiState
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

        OnboardingStep.HOME -> RupeeHome(uiState = homeUiState)
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
private fun RupeeHome(uiState: HomeUiState) {
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
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Hello, ${uiState.userName}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    text = if (uiState.isSeeding) "Seeding local defaults..." else "Local store is ready.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Spacer(modifier = Modifier.height(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MetricPill(label = "Accounts", value = uiState.accountCount.toString(), modifier = Modifier.weight(1f))
                        MetricPill(label = "Cards", value = uiState.cardCount.toString(), modifier = Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MetricPill(label = "Categories", value = uiState.categoryCount.toString(), modifier = Modifier.weight(1f))
                        MetricPill(label = "Buckets", value = uiState.bucketCount.toString(), modifier = Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MetricPill(label = "Canonical", value = uiState.recentTransactionCount.toString(), modifier = Modifier.weight(1f))
                        MetricPill(label = "Inbox", value = uiState.pendingInboxCount.toString(), modifier = Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MetricPill(label = "Candidates", value = uiState.recentCandidateCount.toString(), modifier = Modifier.weight(1f))
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        InspectionSection(
            title = "Recent canonical transactions",
            hasItems = uiState.recentTransactions.isNotEmpty(),
            emptyLabel = "No canonical transactions yet.",
        ) {
            uiState.recentTransactions.forEach { row ->
                InspectionRow(
                    headline = row.headline,
                    subline = row.subline,
                    trailing = row.amountLabel,
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        InspectionSection(
            title = "Recent candidates",
            hasItems = uiState.recentCandidates.isNotEmpty(),
            emptyLabel = "No transaction candidates yet.",
        ) {
            uiState.recentCandidates.forEach { row ->
                InspectionRow(
                    headline = row.headline,
                    subline = row.subline,
                    trailing = row.decisionLabel,
                )
            }
        }
    }
}

@Composable
private fun MetricPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
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
        )
    }
}
