package com.zegrt.rupee.ingestion

/**
 * Structured snapshot of every text-shaped value a notification carried.
 *
 * Per `docs/notification-ingestion-deep-dive.md` §5: parsers read [combinedBody]
 * (everything joined with newlines) and ignore field structure for v1. The
 * structured fields are preserved so v2 rule engine can weight title-matches
 * differently from summary-matches when we get there.
 *
 * `isGroupSummary` mirrors `Notification.FLAG_GROUP_SUMMARY`; the listener
 * skips these so the per-child notifications don't get double-counted.
 */
data class ExtractedNotification(
    val title: String? = null,
    val titleBig: String? = null,
    val text: String? = null,
    val bigText: String? = null,
    val subText: String? = null,
    val infoText: String? = null,
    val summaryText: String? = null,
    val textLines: List<String> = emptyList(),
    val messages: List<String> = emptyList(),
    val ticker: String? = null,
    val actionLabels: List<String> = emptyList(),
    val template: String? = null,
    val isGroupSummary: Boolean = false,
    val combinedBody: String,
) {
    companion object {
        const val FLAG_GROUP_SUMMARY = 0x00000200
    }
}
