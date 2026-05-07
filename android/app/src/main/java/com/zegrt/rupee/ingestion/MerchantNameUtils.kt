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
    private val tails = listOf(" on ", " using ", " via ", " through ")

    fun clean(raw: String?): String {
        if (raw.isNullOrBlank()) return "Unnamed"
        var s = raw.trim()
        // CRED-style "HDFC Credit Card xx1234 at Zomato …" → take after the last " at "
        val atIdx = s.lastIndexOf(" at ", ignoreCase = true)
        if (atIdx >= 0) s = s.substring(atIdx + 4)
        var earliest = Int.MAX_VALUE
        for (tail in tails) {
            val idx = s.indexOf(tail, ignoreCase = true)
            if (idx in 0 until earliest) earliest = idx
        }
        if (earliest != Int.MAX_VALUE) s = s.substring(0, earliest)
        return s.trim().ifBlank { "Unnamed" }
    }

    fun matchesPattern(rawMerchant: String?, pattern: String): Boolean {
        if (rawMerchant.isNullOrBlank() || pattern.isBlank()) return false
        return clean(rawMerchant).equals(pattern.trim(), ignoreCase = true)
    }
}
