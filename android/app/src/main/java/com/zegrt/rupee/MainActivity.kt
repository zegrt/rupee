package com.zegrt.rupee

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zegrt.rupee.home.HomeUiState
import com.zegrt.rupee.home.HomeViewModel
import com.zegrt.rupee.home.HomeViewModelFactory
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
                        viewModelFactory = HomeViewModelFactory(app.localFinanceRepository),
                    )
                }
            }
        }
    }
}

@Composable
private fun RupeeApp(
    viewModelFactory: HomeViewModelFactory,
) {
    val viewModel: HomeViewModel = viewModel(factory = viewModelFactory)
    val uiState by viewModel.uiState.collectAsState()

    RupeeHome(uiState = uiState)
}

@Composable
private fun RupeeHome(uiState: HomeUiState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = "Rupee",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Local data foundation for the India-first personal finance tracker.",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 12.dp),
        )
        Spacer(modifier = Modifier.height(24.dp))
        Card {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Hello, ${uiState.userName}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (uiState.isSeeding) {
                        "Seeding local defaults..."
                    } else {
                        "Local store is ready."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("Accounts: ${uiState.accountCount}")
                Text("Cards: ${uiState.cardCount}")
                Text("Categories: ${uiState.categoryCount}")
                Text("Buckets: ${uiState.bucketCount}")
                Text("Recent transactions: ${uiState.recentTransactionCount}")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RupeeAppPreview() {
    RupeeTheme {
        RupeeHome(
            uiState = HomeUiState(
                userName = "Cyril",
                accountCount = 1,
                cardCount = 1,
                categoryCount = 11,
                bucketCount = 7,
                recentTransactionCount = 2,
                isSeeding = false,
            ),
        )
    }
}
