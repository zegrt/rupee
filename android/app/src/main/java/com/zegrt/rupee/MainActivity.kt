package com.zegrt.rupee

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.zegrt.rupee.ui.flavor.FlavorApp
import com.zegrt.rupee.ui.theme.RupeeTheme

/**
 * Design-research shell. Real wallet logic (ingestion, Room, ViewModels) lives in
 * the rest of the codebase but is unused while we evaluate two design directions.
 * Each product flavor (aviate / vwfndr) provides its own [FlavorApp] composable
 * which renders a fully mocked end-to-end UI in that design language.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RupeeTheme {
                FlavorApp()
            }
        }
    }
}
