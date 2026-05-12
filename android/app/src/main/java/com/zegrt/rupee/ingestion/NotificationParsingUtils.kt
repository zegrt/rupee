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

    // Phrases that anchor a due date: "due on …", "due by …", "payable by …", "due …".
    // We capture the trailing date in three common shapes and normalize to yyyy-MM-dd.
    private val dueDateRegexes = listOf(
        // 15 May 2026 / 15-May-26 / 15/May/2026
        Regex(
            """(?:due|payable)\s*(?:on|by)?\s*(\d{1,2})[\s\-/](jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)[a-z]*[\s\-/]?(\d{2,4})?""",
            RegexOption.IGNORE_CASE,
        ),
        // 15/05/2026 / 15-05-2026 / 15.05.26
        Regex(
            """(?:due|payable)\s*(?:on|by)?\s*(\d{1,2})[\-/.](\d{1,2})[\-/.](\d{2,4})""",
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

    private fun cleanupEntityName(value: String): String {
        return value
            .trim()
            .trim(',', '.', ':', ';', '-', ' ')
            .replace(Regex("""\s{2,}"""), " ")
    }
}
