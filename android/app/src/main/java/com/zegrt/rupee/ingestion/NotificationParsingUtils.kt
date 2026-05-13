package com.zegrt.rupee.ingestion

internal object NotificationParsingUtils {
    private val amountRegex = Regex(
        """(?:rs\.?|inr|₹)\s*([0-9]+(?:,[0-9]{3})*(?:\.[0-9]{1,2})?)""",
        RegexOption.IGNORE_CASE,
    )

    private val cardDigitsRegexes = listOf(
        Regex("""(?:xx|xx\s*card|card|a/c|account|ending)\D{0,12}(\d{4})""", RegexOption.IGNORE_CASE),
        Regex("""[*xX]{2,}\s*(\d{4})"""),
    )

    private val monthAbbrev = mapOf(
        "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6,
        "jul" to 7, "aug" to 8, "sep" to 9, "sept" to 9, "oct" to 10, "nov" to 11, "dec" to 12,
    )

    // Phrases that anchor a due date: "due on …", "due by …", "payable by …",
    // "by 15 May", "Total due Rs. 1000 by 15 May 2026". We allow up to ~60 chars of
    // intermediate text between the anchor word and the date so bodies that quote
    // the amount inline still match. Restricting to BILL_DUE/EMI candidates upstream
    // keeps the false-positive rate low even with the looser pattern.
    private val dueDateRegexes = listOf(
        // 15 May 2026 / 15-May-26 / 15/May/2026
        Regex(
            """\b(?:due|payable|by)\b[^\n]{0,60}?\b(\d{1,2})[\s\-/](jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)[a-z]*(?:[\s\-/]+(\d{2,4}))?""",
            RegexOption.IGNORE_CASE,
        ),
        // 15/05/2026 / 15-05-2026 / 15.05.26
        Regex(
            """\b(?:due|payable|by)\b[^\n]{0,60}?\b(\d{1,2})[\-/.](\d{1,2})[\-/.](\d{2,4})""",
            RegexOption.IGNORE_CASE,
        ),
    )

    fun extractAmountMinor(text: String): Long? {
        val match = amountRegex.find(text) ?: return null
        val normalized = match.groupValues[1].replace(",", "")
        return normalized.toBigDecimalOrNull()?.movePointRight(2)?.toLong()
    }

    fun extractDueDateIso(text: String, todayYear: Int = java.time.Year.now().value): String? {
        for ((index, regex) in dueDateRegexes.withIndex()) {
            val m = regex.find(text) ?: continue
            val day = m.groupValues[1].toIntOrNull() ?: continue
            val (month, yearRaw) = if (index == 0) {
                val m2 = monthAbbrev[m.groupValues[2].lowercase()] ?: continue
                m2 to m.groupValues.getOrNull(3)
            } else {
                val m2 = m.groupValues[2].toIntOrNull() ?: continue
                m2 to m.groupValues.getOrNull(3)
            }
            val year = normalizeYear(yearRaw, todayYear) ?: todayYear
            return runCatching {
                java.time.LocalDate.of(year, month, day).toString()
            }.getOrNull()
        }
        return null
    }

    private fun normalizeYear(raw: String?, todayYear: Int): Int? {
        if (raw.isNullOrBlank()) return todayYear
        val n = raw.toIntOrNull() ?: return null
        return if (n < 100) 2000 + n else n
    }

    fun extractMerchant(text: String, patterns: List<Regex>): String? {
        return patterns
            .asSequence()
            .mapNotNull { regex -> regex.find(text)?.groupValues?.getOrNull(1) }
            .map { cleanupEntityName(it) }
            .firstOrNull { it.isNotBlank() }
    }

    fun extractMaskedDigits(text: String): String? {
        return cardDigitsRegexes
            .asSequence()
            .mapNotNull { regex -> regex.find(text)?.groupValues?.getOrNull(1) }
            .map { it.trim() }
            .firstOrNull { it.length == 4 }
    }

    fun containsAny(text: String, needles: List<String>): Boolean {
        return needles.any { needle -> needle in text }
    }

    /**
     * Classifies a body as money-IN, money-OUT, or unknown by inspecting the
     * verb vocabulary. Centralised so every parser routes the same way — Kotak
     * and the Generic fallback used to both force SPEND regardless of verb,
     * which meant a "Rs 50,000 credited to A/c XX1234" body was getting
     * recorded as a spend and inflating the user's monthly burn.
     *
     * Order matters: we check credit-side verbs first because some bodies
     * mention both ("Rs.500 debited and Rs.500 credited" is rare but possible
     * during reversals). When ambiguous, the parser's `transactionKind` should
     * fall back to UNKNOWN — the user adjudicates in Inbox.
     */
    fun classifyDirection(text: String): MoneyDirection {
        val lower = text.lowercase()
        val hasCredit = CREDIT_VERBS.any { it in lower }
        val hasDebit = DEBIT_VERBS.any { it in lower }
        return when {
            hasCredit && !hasDebit -> MoneyDirection.IN
            hasDebit && !hasCredit -> MoneyDirection.OUT
            hasDebit && hasCredit -> MoneyDirection.UNKNOWN
            else -> MoneyDirection.UNKNOWN
        }
    }

    enum class MoneyDirection { IN, OUT, UNKNOWN }

    private val CREDIT_VERBS = listOf(
        "credited",
        " credit ",
        "received from",
        "received via",
        "received rs",
        "received inr",
        "received ₹",
        "deposited",
        "salary credit",
    )

    private val DEBIT_VERBS = listOf(
        "debited",
        " debit ",
        "spent",
        "paid",
        "sent via",
        "sent to",
        "sent rs",
        "sent inr",
        "transferred",
        "withdrawn",
        "deducted",
        "auto-debit",
        "auto debit",
        "swiped",
        "charged",
    )

    data class NetworkReference(val id: String, val type: String)

    // Ordered by specificity. RRN appears in card-network notifications;
    // labelled "UPI Ref" / "IMPS Ref" patterns are explicit; the bare-number
    // catch-alls run last so a labelled match always wins.
    private val networkRefPatterns: List<Pair<Regex, String>> = listOf(
        Regex(
            """\b(?:UPI(?:\s+Ref(?:erence)?(?:\s*(?:No|Number))?\.?)?|UPI\s+txn\s+id|UTR)\s*[:#]?\s*([A-Z0-9]{8,22})\b""",
            RegexOption.IGNORE_CASE,
        ) to "UPI",
        Regex(
            """\bIMPS\s+(?:Ref(?:erence)?(?:\s*No)?\.?|ID)\s*[:#]?\s*([A-Z0-9]{8,22})\b""",
            RegexOption.IGNORE_CASE,
        ) to "IMPS",
        Regex(
            """\bNEFT\s+(?:Ref(?:erence)?(?:\s*No)?\.?|UTR)\s*[:#]?\s*([A-Z0-9]{8,22})\b""",
            RegexOption.IGNORE_CASE,
        ) to "NEFT",
        Regex(
            """\bRTGS\s+(?:Ref(?:erence)?(?:\s*No)?\.?|UTR)\s*[:#]?\s*([A-Z0-9]{8,22})\b""",
            RegexOption.IGNORE_CASE,
        ) to "RTGS",
        Regex(
            """\bRRN\s*[:#]?\s*(\d{10,14})\b""",
            RegexOption.IGNORE_CASE,
        ) to "CARD_AUTH",
        // Generic "Ref No 123456789012" with no network prefix. Used when the
        // body says only "Ref No 1234567890123" — assume UPI (most common in
        // notifications today) and let downstream callers refine if needed.
        Regex(
            """\bRef(?:erence)?(?:\s*No)?\.?\s*[:#]?\s*([0-9]{10,18})\b""",
            RegexOption.IGNORE_CASE,
        ) to "UPI",
    )

    fun extractNetworkReference(text: String): NetworkReference? {
        for ((regex, type) in networkRefPatterns) {
            val match = regex.find(text) ?: continue
            val id = match.groupValues.getOrNull(1)?.trim().orEmpty()
            if (id.isNotBlank()) return NetworkReference(id = id, type = type)
        }
        return null
    }

    private fun cleanupEntityName(value: String): String {
        return value
            .trim()
            .trim(',', '.', ':', ';', '-', ' ')
            .replace(Regex("""\s{2,}"""), " ")
    }
}
