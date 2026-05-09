package com.zegrt.rupee.budget

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.zegrt.rupee.data.repository.LocalFinanceRepository
import java.time.LocalDate
import java.time.YearMonth

class BudgetAlertManager(private val context: Context) {

    fun registerChannel() {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Budget alerts",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "Notifies you when monthly spend is near or over your budget" }
        )
    }

    suspend fun checkAndNotify(repository: LocalFinanceRepository, today: LocalDate = LocalDate.now()) {
        val budget = repository.getBudgetSnapshot(today) ?: return
        if (budget.limitMinor <= 0L) return

        val spent = repository.getSpentSnapshot(today)
        val ratio = spent.toDouble() / budget.limitMinor.toDouble()
        val threshold = budget.alertThresholdPercent

        val level = when {
            ratio >= 1.0 -> AlertLevel.OVER
            ratio >= threshold -> AlertLevel.NEAR
            else -> return
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val monthKey = "alerted_${YearMonth.from(today)}"
        val lastLevel = prefs.getString(monthKey, AlertLevel.NONE.name)
        if (lastLevel == level.name || (lastLevel == AlertLevel.OVER.name && level == AlertLevel.NEAR)) return

        prefs.edit().putString(monthKey, level.name).apply()

        val budgetLabel = formatRupees(budget.limitMinor)
        val spentLabel = formatRupees(spent)
        val (title, text) = when (level) {
            AlertLevel.OVER -> "Over budget" to "You've spent $spentLabel — exceeded your $budgetLabel monthly budget."
            AlertLevel.NEAR -> "Budget alert" to "You've spent ${(ratio * 100).toInt()}% of your $budgetLabel monthly budget."
            AlertLevel.NONE -> return
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
    }

    private fun formatRupees(minor: Long): String {
        val rupees = minor / 100.0
        return when {
            rupees >= 100_000 -> "₹%.1fL".format(rupees / 100_000)
            rupees >= 1_000 -> "₹%.0fK".format(rupees / 1_000)
            else -> "₹%.0f".format(rupees)
        }
    }

    private enum class AlertLevel { NONE, NEAR, OVER }

    companion object {
        const val CHANNEL_ID = "rupee_budget_alerts"
        private const val PREFS_NAME = "rupee_budget_alert_prefs"
        private const val NOTIFICATION_ID = 1001
    }
}
