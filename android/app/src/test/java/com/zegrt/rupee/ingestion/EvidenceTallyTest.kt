package com.zegrt.rupee.ingestion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Direct unit coverage for the S1.3 pilot helper. The integration tests
 * in `NotificationParserParseTest` exercise it through
 * `GenericUpiNotificationParser`, but the band table and `addIf` /
 * `signals` contracts deserve their own pins.
 *
 * The helper is internal but visible from the same package; this test
 * lives in `ingestion/` deliberately.
 */
class EvidenceTallyTest {

    @Test
    fun add_accumulates_pointsAndSignals() {
        val tally = EvidenceTally()
            .add("amount", 1)
            .add("merchant", 3)
            .add("digits", 2)
        assertEquals(6, tally.totalPoints())
        assertEquals(
            listOf("amount" to 1, "merchant" to 3, "digits" to 2),
            tally.signals(),
        )
    }

    @Test
    fun addIf_falseGuard_isNoOp() {
        val tally = EvidenceTally()
            .add("amount", 1)
            .addIf(condition = false, name = "merchant", weight = 3)
            .addIf(condition = true, name = "digits", weight = 2)
        assertEquals(3, tally.totalPoints())
        assertEquals(
            listOf("amount" to 1, "digits" to 2),
            tally.signals(),
        )
    }

    @Test
    fun signals_returnsImmutableSnapshot() {
        // The toList() copy in signals() means the caller can't mutate the
        // tally's internal list. Guard against a future refactor that drops
        // it.
        val tally = EvidenceTally().add("amount", 1)
        val signalsView = tally.signals()
        tally.add("merchant", 3)
        // The view we grabbed earlier still reads the pre-add value.
        assertEquals(1, signalsView.size)
        assertEquals(2, tally.signals().size)
    }

    // Band table — pinned per the kdoc so a future tune of the mapping
    // surfaces here, not silently in the dump-replay corpus.
    @Test
    fun pointsToConfidence_bandTable() {
        // HIGH band ceiling
        assertEquals(0.90, EvidenceTally.pointsToConfidence(7), 1e-9)
        assertEquals(0.90, EvidenceTally.pointsToConfidence(6), 1e-9)
        // HIGH band lower
        assertEquals(0.85, EvidenceTally.pointsToConfidence(5), 1e-9)
        // MEDIUM upper (amount + merchant)
        assertEquals(0.78, EvidenceTally.pointsToConfidence(4), 1e-9)
        // MEDIUM lower (amount + digits OR amount + ref)
        assertEquals(0.62, EvidenceTally.pointsToConfidence(3), 1e-9)
        // LOW upper
        assertEquals(0.55, EvidenceTally.pointsToConfidence(2), 1e-9)
        // LOW floor (amount alone)
        assertEquals(0.30, EvidenceTally.pointsToConfidence(1), 1e-9)
        // Nothing extracted
        assertEquals(0.30, EvidenceTally.pointsToConfidence(0), 1e-9)
        // Anti-signals push below floor — clamps to LOW band ceiling at most.
        assertEquals(0.30, EvidenceTally.pointsToConfidence(-2), 1e-9)
    }

    @Test
    fun score_matchesPointsToConfidenceOnInstance() {
        // Instance method and static helper must agree — the instance is
        // just a convenience wrapper.
        val tally = EvidenceTally()
            .add("amount", 1)
            .add("merchant", 3)
        assertEquals(EvidenceTally.pointsToConfidence(4), tally.score(), 1e-9)
    }

    @Test
    fun band_thresholdsMatchDecisionEngineBands() {
        // The band table is documented to map onto NotificationDecisionEngine
        // (HIGH ≥ 0.85, MEDIUM ≥ 0.6, LOW below). Re-assert from the
        // EvidenceTally side so a future change to either constant set
        // forces a conversation here.
        val highCases = listOf(5, 6, 7, 10)
        val mediumCases = listOf(3, 4)
        val lowCases = listOf(0, 1, 2)
        for (p in highCases) assertTrue("expected $p HIGH", EvidenceTally.pointsToConfidence(p) >= 0.85)
        for (p in mediumCases) {
            val c = EvidenceTally.pointsToConfidence(p)
            assertTrue("expected $p in MEDIUM band, got $c", c in 0.60..0.85)
        }
        for (p in lowCases) assertTrue("expected $p LOW", EvidenceTally.pointsToConfidence(p) < 0.60)
    }
}
