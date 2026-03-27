package com.zegrt.rupee.onboarding

import android.content.Context

class OnboardingPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("rupee_onboarding", Context.MODE_PRIVATE)

    fun isCompleted(): Boolean = prefs.getBoolean(KEY_COMPLETED, false)

    fun setCompleted(completed: Boolean) {
        prefs.edit().putBoolean(KEY_COMPLETED, completed).apply()
    }

    private companion object {
        const val KEY_COMPLETED = "completed"
    }
}
