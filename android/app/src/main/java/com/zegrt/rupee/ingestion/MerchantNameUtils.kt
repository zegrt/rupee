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
    // Pairs of (uppercase-match-tokens → canonical brand name).
    // Tokens are matched as whole-word substrings inside the upper-cased
    // merchant string (the [normalizeFuelBrand] matcher pads with spaces
    // and replaces non-alphanumerics with spaces before testing). So
    // "IOC" matches "IOC OUTLET, MUMBAI" but NOT "BIOCON" (the pharma
    // company) because there's no whitespace boundary between "B" and
    // "IOC" in BIOCON.
    //
    // Bare "HP" / "BP" are deliberately omitted — too short / too
    // ambiguous against non-fuel retailers (HP electronics, Bharat-
    // prefixed financial companies). User can add a trust rule manually
    // for those if needed.
    private val fuelBrands: List<Pair<List<String>, String>> = listOf(
        listOf("IOC", "IOCL", "INDIAN OIL") to "IOC",
        listOf("HPCL", "HINDUSTAN PETROLEUM") to "HPCL",
        listOf("BPCL", "BHARAT PETROLEUM") to "BPCL",
        listOf("SHELL", "SHELL PETROL", "SHELL FUEL") to "Shell",
        listOf("NAYARA") to "Nayara",
        listOf("ESSAR PETROL", "ESSAR FUEL") to "Essar",
    )

    private val tails = listOf(
        " on ",
        " using ",
        " via ",
        " through ",
        " successfully",
        // " was " catches "Zomato was successful. UPI Ref" → "Zomato". Reach for ". " is
        // tempting but clips legit names like "St. Patrick's Restaurant" → "St".
        " was ",
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
        // Fuel-brand normalisation. Petrol-station bodies typically carry
        // a location and outlet code appended to the brand: "IOC OUTLET
        // MUMBAI 12345", "HPCL DELHI PETROL PUMP". A user who sets a
        // merchant trust rule on "IOC" should have it fire across every
        // outlet of that brand, not just the one they happened to
        // categorise first. Normalise to the bare brand if matched.
        // Whitelist is conservative — only brands whose tokens are
        // unambiguous enough to not collide with non-fuel merchants
        // (no bare "HP" / "BP", which could be electronics retailers
        // and Bharat-prefixed companies respectively).
        normalizeFuelBrand(s)?.let { return it }
        return s.ifBlank { "Unnamed" }
    }

    /**
     * If [cleaned] contains a recognised fuel-brand token, return the bare
     * brand name. Otherwise null. Exposed for testing the whitelist
     * without round-tripping through [clean].
     */
    internal fun normalizeFuelBrand(cleaned: String): String? {
        // Pad with spaces and replace non-alphanumerics with spaces so we
        // can test for `" TOKEN "` cleanly without writing regex per
        // brand. "IOC OUTLET, MUMBAI" → " IOC OUTLET  MUMBAI " — the IOC
        // token at the start now has a leading space, and commas /
        // punctuation between words become spaces so multi-word brand
        // names like "INDIAN OIL" still match.
        val padded = " " + cleaned.uppercase().replace(Regex("[^A-Z0-9 ]"), " ") + " "
        // Collapse runs of whitespace so " IOC   OUTLET " becomes
        // " IOC OUTLET " — the brand-token list uses single-space form.
        val normalised = padded.replace(Regex(" +"), " ")
        for ((tokens, brand) in fuelBrands) {
            if (tokens.any { " $it ".replace(Regex(" +"), " ") in normalised }) return brand
        }
        return null
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
