package com.zegrt.rupee.ingestion

/**
 * Additive evidence tally for parser confidence (backlog S1.3 pilot).
 *
 * Today every parser hardcodes its own `when` ladder of confidence floors
 * (0.62 / 0.78 / 0.85, etc.) and tuning means editing nine files. With a
 * tally, each parser declares which signals it found and at what weight;
 * a single `pointsToConfidence` table maps the total to the same
 * confidence band `NotificationDecisionEngine` already reads.
 *
 * **Weight convention (current pilot):**
 *
 * | Signal                            | Weight |
 * |-----------------------------------|--------|
 * | `amountMinor` extracted           | 1      |
 * | Named merchant (regex hit)        | 3      |
 * | Masked digits / account ending    | 2      |
 * | Network reference (UPI / NEFT id) | 2      |
 * | Canonical verb in body            | 1      |
 * | Soft anti-signal (advisory text)  | −5     |
 *
 * Point thresholds map to the same 0.85 / 0.6 bands
 * `NotificationDecisionEngine.HIGH_CONFIDENCE` / `MEDIUM_CONFIDENCE`
 * already use, so a parser switching to the tally doesn't change which
 * tier its rows land in for the existing dump corpus — only how tunable
 * the scoring becomes.
 *
 * Pilot is `GenericUpiNotificationParser` (per backlog Sprint 1).
 * Sprint 2 migrates the other parsers if the pattern feels right; if
 * not, the per-parser `when` ladders stay where they are.
 */
internal class EvidenceTally {

    private var points: Int = 0
    private val signals: MutableList<Pair<String, Int>> = mutableListOf()

    /** Add a signal with [weight] points. Returns `this` for chaining. */
    fun add(name: String, weight: Int): EvidenceTally {
        points += weight
        signals += name to weight
        return this
    }

    /** Conditionally add a signal — convenience for `.add(...)` guarded by a Boolean. */
    fun addIf(condition: Boolean, name: String, weight: Int): EvidenceTally =
        if (condition) add(name, weight) else this

    /** Raw points so callers can pass it through to telemetry. */
    fun totalPoints(): Int = points

    /** Signal list (name + weight) for debugging / observability. */
    fun signals(): List<Pair<String, Int>> = signals.toList()

    /**
     * Map the accumulated points to `NotificationParseResult.parseConfidence`
     * (a 0.0–1.0 double). The mapping preserves the existing parser bands so
     * a switch to the tally doesn't reshuffle dump-replay tier outputs:
     *
     * - ≥ 6 → 0.90 (HIGH — amount + named merchant + a secondary signal)
     * - 5   → 0.85 (HIGH — amount + named merchant + weak corroboration)
     * - 4   → 0.78 (MEDIUM — amount + named merchant)
     * - 3   → 0.62 (MEDIUM — amount + masked digits OR amount + network ref)
     * - 2   → 0.55 (LOW — amount + a weak signal)
     * - ≤ 1 → 0.30 (LOW — amount alone, or nothing)
     */
    fun score(): Double = pointsToConfidence(points)

    companion object {
        fun pointsToConfidence(points: Int): Double = when {
            points >= 6 -> 0.90
            points == 5 -> 0.85
            points == 4 -> 0.78
            points == 3 -> 0.62
            points == 2 -> 0.55
            else -> 0.30
        }
    }
}
