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

    fun extractAmountMinor(text: String): Long? {
        val match = amountRegex.find(text) ?: return null
        val normalized = match.groupValues[1].replace(",", "")
        return normalized.toBigDecimalOrNull()?.movePointRight(2)?.toLong()
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
