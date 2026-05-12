package com.zegrt.rupee.ingestion

import android.app.Notification
import android.os.Bundle
import androidx.core.app.NotificationCompat

/**
 * Pulls every text-shaped value from a notification into one structured snapshot.
 *
 * Why: real bank notifications put transaction bodies in `EXTRA_BIG_TEXT` (the
 * expanded view) and often leave `EXTRA_TEXT` empty. Reading a single field
 * silently drops them. Walking the whole Bundle for any CharSequence or
 * CharSequence[] is future-proof against new notification styles and per-app
 * extras a bank might invent.
 *
 * Pure-Kotlin core ([extract]) is testable via plain Map; the Android-side
 * [fromNotification] adapts a real `Notification` for the listener service.
 *
 * See `docs/notification-ingestion-deep-dive.md` for the full rationale.
 */
object NotificationExtractor {

    /**
     * Adapt an Android `Notification` into an [ExtractedNotification]. Reads
     * extras, ticker, action labels, MessagingStyle messages, and flags in
     * one pass.
     */
    fun fromNotification(notification: Notification): ExtractedNotification {
        val extras = notification.extras ?: Bundle()
        val asMap = bundleToMap(extras)

        // MessagingStyle messages live as Parcelable[] of bundles — easier to
        // walk via the support helper than to decode by hand.
        val messages: List<String> = runCatching {
            NotificationCompat.MessagingStyle
                .extractMessagingStyleFromNotification(notification)
                ?.messages
                ?.mapNotNull { it.text?.toString()?.trim()?.takeIf(String::isNotBlank) }
                ?: emptyList()
        }.getOrDefault(emptyList())

        val actionLabels: List<String> = notification.actions
            ?.mapNotNull { it.title?.toString()?.trim()?.takeIf(String::isNotBlank) }
            ?: emptyList()

        return extract(
            extras = asMap,
            flags = notification.flags,
            ticker = notification.tickerText?.toString(),
            messages = messages,
            actionLabels = actionLabels,
        )
    }

    /**
     * Core extraction logic. Pure data in, pure data out — no Android types.
     * Tests construct an ExtractedNotification by passing a Map directly.
     */
    fun extract(
        extras: Map<String, Any?>,
        flags: Int = 0,
        ticker: String? = null,
        messages: List<String> = emptyList(),
        actionLabels: List<String> = emptyList(),
    ): ExtractedNotification {
        val title = extras.charSeq(NotificationCompat.EXTRA_TITLE)
        val titleBig = extras.charSeq(NotificationCompat.EXTRA_TITLE_BIG)
        val text = extras.charSeq(NotificationCompat.EXTRA_TEXT)
        val bigText = extras.charSeq(NotificationCompat.EXTRA_BIG_TEXT)
        val subText = extras.charSeq(NotificationCompat.EXTRA_SUB_TEXT)
        val infoText = extras.charSeq(NotificationCompat.EXTRA_INFO_TEXT)
        val summary = extras.charSeq(NotificationCompat.EXTRA_SUMMARY_TEXT)
        val template = extras.charSeq(NotificationCompat.EXTRA_TEMPLATE)
        val tickerTrimmed = ticker?.trim()?.takeIf(String::isNotBlank)

        val textLines: List<String> = (extras[NotificationCompat.EXTRA_TEXT_LINES] as? Array<*>)
            ?.mapNotNull { (it as? CharSequence)?.toString()?.trim()?.takeIf(String::isNotBlank) }
            ?: emptyList()

        val isGroupSummary = (flags and ExtractedNotification.FLAG_GROUP_SUMMARY) != 0

        // Build combinedBody as a deduplicated newline-joined union of every
        // text-bearing field. Use LinkedHashSet to preserve a stable order
        // (title → bigText → text → … ) while dropping duplicates that occur
        // when banks put the same string in both EXTRA_TEXT and EXTRA_BIG_TEXT.
        val combined = linkedSetOf<String>()
        listOfNotNull(
            title,
            titleBig,
            bigText,
            text,
            subText,
            summary,
            infoText,
            tickerTrimmed,
        ).forEach(combined::add)
        textLines.forEach(combined::add)
        messages.forEach(combined::add)
        actionLabels.forEach(combined::add)

        // Also sweep any remaining CharSequence values in extras that the
        // standard fields above missed — covers app-specific keys like
        // "android.bigText" being absent but a custom "android.transactionText"
        // being present. Cheap insurance against future bundle shapes.
        for ((key, value) in extras) {
            if (key.startsWith("android.large") || key == NotificationCompat.EXTRA_PICTURE) continue
            when (value) {
                is CharSequence -> value.toString().trim().takeIf(String::isNotBlank)?.let(combined::add)
                is Array<*> -> value.forEach { item ->
                    if (item is CharSequence) {
                        item.toString().trim().takeIf(String::isNotBlank)?.let(combined::add)
                    }
                }
            }
        }

        return ExtractedNotification(
            title = title,
            titleBig = titleBig,
            text = text,
            bigText = bigText,
            subText = subText,
            infoText = infoText,
            summaryText = summary,
            textLines = textLines,
            messages = messages,
            ticker = tickerTrimmed,
            actionLabels = actionLabels,
            template = template,
            isGroupSummary = isGroupSummary,
            combinedBody = combined.joinToString("\n"),
        )
    }

    private fun Map<String, Any?>.charSeq(key: String): String? =
        (this[key] as? CharSequence)?.toString()?.trim()?.takeIf(String::isNotBlank)

    private fun bundleToMap(bundle: Bundle): Map<String, Any?> {
        val map = HashMap<String, Any?>(bundle.size())
        for (key in bundle.keySet()) {
            @Suppress("DEPRECATION")
            map[key] = bundle.get(key)
        }
        return map
    }
}
