package com.zegrt.rupee.ingestion

/**
 * Display- and matching-time merchant cleanup. Trims metadata tails and prefers
 * the segment after a leading bank/card prefix for CRED-style bodies. Does not
 * mutate the canonical [com.zegrt.rupee.data.local.entity.CanonicalTransactionEntity.merchantName]
 * stored in the DB — that stays as the parser produced it.
 *
 * Used by the dashboard/inbox/transactions UIs and by [MerchantTrustRule]
 * matching so the user's "Always trust Swiggy" rule actually fires when a
 * future notification is parsed to "Swiggy using UPI".
 */
object MerchantNameUtils {
    private val tails = listOf(
        " on ",
        " using ",
        " via ",
        " through ",
        " successfully",
        " was ",
        ". ",
    )
    private val leadingTailWords = listOf("using", "via", "on", "through")
    // After all stripping, if the leftover is just a bare keyword like "at" or "using",
    // treat the whole thing as noise — there's no merchant in there.
    private val noiseTokens = setOf("at", "using", "via", "on", "through")

    fun clean(raw: String?): String {
        if (raw.isNullOrBlank()) return "Unnamed"
        var s = raw.trim()
        // CRED-style "HDFC Credit Card xx1234 at Zomato …" → take after the last " at "
        val atIdx = s.lastIndexOf(" at ", ignoreCase = true)
        if (atIdx >= 0) s = s.substring(atIdx + 4)
        // Trailing bare " at" with nothing after — body said "spent at <X>" but X is missing,
        // so the prefix is bank-statement preamble, not a merchant. Treat as Unnamed.
        if (s.endsWith(" at", ignoreCase = true)) return "Unnamed"
        var earliest = Int.MAX_VALUE
        for (tail in tails) {
            val idx = s.indexOf(tail, ignoreCase = true)
            if (idx in 0 until earliest) earliest = idx
        }
        if (earliest != Int.MAX_VALUE) s = s.substring(0, earliest)
        s = s.trim()
        // Fallback for inputs whose only signal was a leading tail word ("using UPI", "on …").
        // Only fires when nothing above produced a real merchant segment, to avoid clipping
        // genuine merchants whose names happen to start with "On Track …".
        if (s.isNotEmpty()) {
            val firstWord = s.substringBefore(' ')
            if (firstWord.lowercase() in leadingTailWords && atIdx < 0 && earliest == Int.MAX_VALUE) {
                s = ""
            }
        }
        if (s.lowercase() in noiseTokens) return "Unnamed"
        return s.ifBlank { "Unnamed" }
    }

    fun matchesPattern(rawMerchant: String?, pattern: String): Boolean {
        if (rawMerchant.isNullOrBlank() || pattern.isBlank()) return false
        return clean(rawMerchant).equals(pattern.trim(), ignoreCase = true)
    }

    /**
     * Convenience for parsers populating `toEntityName` (the canonical merchant name).
     * Returns the cleaned form, or `null` when the raw input has no usable merchant —
     * keeps `merchantRaw` raw for fingerprinting/trust matching while letting downstream
     * code rely on `toEntityName` being display-ready.
     */
    fun cleanForEntity(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val cleaned = clean(raw)
        return if (cleaned == "Unnamed") null else cleaned
    }
}
