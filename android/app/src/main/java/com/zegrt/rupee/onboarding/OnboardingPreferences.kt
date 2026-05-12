package com.zegrt.rupee.onboarding

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OnboardingPreferences(private val context: Context) {
    // SharedPreferences.getBoolean blocks on first access (loads the XML from disk).
    // The synchronous variant is preserved for callers in non-suspend code paths
    // that need a hot value after init, but new code should prefer isCompletedAsync.
    private val prefs by lazy {
        context.getSharedPreferences("rupee_onboarding", Context.MODE_PRIVATE)
    }

    fun isCompleted(): Boolean = prefs.getBoolean(KEY_COMPLETED, false)

    suspend fun isCompletedAsync(): Boolean = withContext(Dispatchers.IO) {
        prefs.getBoolean(KEY_COMPLETED, false)
    }

    fun setCompleted(completed: Boolean) {
        prefs.edit().putBoolean(KEY_COMPLETED, completed).apply()
    }

    private companion object {
        const val KEY_COMPLETED = "completed"
    }
}
