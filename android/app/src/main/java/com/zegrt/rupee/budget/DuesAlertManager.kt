package com.zegrt.rupee.budget

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import com.zegrt.rupee.data.repository.LocalFinanceRepository
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * PRD §12.13: recurring-due-soon and credit-card-bill-due alerts. Cheap pull model —
 * runs on app open / on resume / after ingestion. Dedupe by (item id, due date) in
 * SharedPreferences so a single charge only nags the user once per due cycle.
 */
class DuesAlertManager(private val context: Context) {

    fun registerChannel() {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Upcoming dues",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "Reminders for credit card bills, EMIs, and recurring charges" }
        )
    }

    suspend fun checkAndNotify(
        repository: LocalFinanceRepository,
        today: LocalDate = LocalDate.now(),
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val nm = NotificationManagerCompat.from(context)
        // Gate the whole sweep on the user's notification permission. If they've
        // denied POST_NOTIFICATIONS we skip everything — and importantly skip
        // the dedupe pref writes too so they get the alert the next time
        // permission is restored.
        if (!nm.areNotificationsEnabled()) return

        // Credit card statements: fire 2 days out, then on the due date itself.
        repository.getCreditCardsSnapshot().forEach { card ->
            val due = parseDate(card.statementDueDate) ?: return@forEach
            val amount = card.statementDueAmountMinor ?: return@forEach
            val days = (due.toEpochDay() - today.toEpochDay()).toInt()
            if (days !in 0..LEAD_DAYS_CARD) return@forEach
            val key = "card_${card.id}_${card.statementDueDate}"
            if (prefs.getBoolean(key, false)) return@forEach
            prefs.edit { putBoolean(key, true) }
            nm.notifyIfAllowed(
                id = NOTIF_BASE_CARD + card.id.hashCode(),
                title = "${card.displayName} bill due ${dueLabel(days, due)}",
                text = "${formatRupees(amount)} due — set up the payment or auto-debit.",
            )
        }

        // EMIs: fire 2 days out.
        repository.getEmiPlansSnapshot().forEach { plan ->
            val due = parseDate(plan.nextDueAt) ?: return@forEach
            val days = (due.toEpochDay() - today.toEpochDay()).toInt()
            if (days !in 0..LEAD_DAYS_EMI) return@forEach
            val key = "emi_${plan.id}_${plan.nextDueAt}"
            if (prefs.getBoolean(key, false)) return@forEach
            prefs.edit { putBoolean(key, true) }
            nm.notifyIfAllowed(
                id = NOTIF_BASE_EMI + plan.id.hashCode(),
                title = "${plan.name} EMI due ${dueLabel(days, due)}",
                text = "${formatRupees(plan.monthlyAmountMinor)} scheduled.",
            )
        }

        // Recurring (confirmed): fire 1 day out so the user can fund the account.
        repository.getRecurringPatternsSnapshot()
            .filter { it.isConfirmed && !it.isDismissed }
            .forEach { pattern ->
                val due = parseDate(pattern.nextExpectedAt) ?: return@forEach
                val days = (due.toEpochDay() - today.toEpochDay()).toInt()
                if (days !in 0..LEAD_DAYS_RECURRING) return@forEach
                val key = "rec_${pattern.id}_${pattern.nextExpectedAt}"
                if (prefs.getBoolean(key, false)) return@forEach
                prefs.edit { putBoolean(key, true) }
                nm.notifyIfAllowed(
                    id = NOTIF_BASE_RECURRING + pattern.id.hashCode(),
                    title = "${pattern.merchantPattern} ${dueLabel(days, due)}",
                    text = "${formatRupees(pattern.expectedAmountMinor)} typically debited around this time.",
                )
            }
    }

    private fun NotificationManagerCompat.notifyIfAllowed(id: Int, title: String, text: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching { notify(id, notification) }
    }

    private fun parseDate(iso: String?): LocalDate? {
        if (iso.isNullOrBlank()) return null
        // DATE-PARSE-LOGGING (Sprint 4). A malformed due-date silently
        // dropped the alert without telling anyone; a tester report
        // ("the bill due-date notification didn't fire") now has a
        // logcat line to bisect against.
        return runCatching { LocalDate.parse(iso.take(10)) }
            .onFailure { t -> Log.w("Rupee", "DuesAlertManager.parseDate failed for iso=$iso", t) }
            .getOrNull()
    }

    private fun dueLabel(daysAway: Int, date: LocalDate): String = when (daysAway) {
        0 -> "today"
        1 -> "tomorrow"
        else -> "on ${date.format(LABEL_FORMATTER)}"
    }

    private fun formatRupees(minor: Long): String {
        val rupees = minor / 100.0
        return when {
            rupees >= 100_000 -> "₹%.1fL".format(rupees / 100_000)
            rupees >= 1_000 -> "₹%.0fK".format(rupees / 1_000)
            else -> "₹%.0f".format(rupees)
        }
    }

    companion object {
        const val CHANNEL_ID = "rupee_dues_alerts"
        private const val PREFS_NAME = "rupee_dues_alert_prefs"
        private const val NOTIF_BASE_CARD = 2_000_000
        private const val NOTIF_BASE_EMI = 3_000_000
        private const val NOTIF_BASE_RECURRING = 4_000_000
        private const val LEAD_DAYS_CARD = 2
        private const val LEAD_DAYS_EMI = 2
        private const val LEAD_DAYS_RECURRING = 1
        private val LABEL_FORMATTER = DateTimeFormatter.ofPattern("d MMM")
    }
}
