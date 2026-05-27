package com.zegrt.rupee.data.repository

import com.zegrt.rupee.data.repository.LocalFinanceRepository.Companion.defaultDiagCaptureForStage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DIAG-CAPTURE-TOGGLE stage default — pins the rule a freshly-installed
 * app uses when no prior toggle state exists. The rule is intentionally
 * simple (substring on versionName) so it's easy to predict from the
 * gradle versionName alone, but easy to get wrong by mis-applying.
 */
class DiagnosticCaptureStageDefaultTest {

    @Test
    fun `alpha builds default ON`() {
        assertTrue(defaultDiagCaptureForStage("0.15.0-alpha.1"))
        assertTrue(defaultDiagCaptureForStage("0.15.0-alpha.2"))
        assertTrue(defaultDiagCaptureForStage("0.99.0-alpha.42"))
    }

    @Test
    fun `closed-beta builds default ON via the -beta substring`() {
        // Closed-beta naming convention; the heuristic will get revisited
        // when we cut the first beta and need to distinguish closed-beta
        // (which keeps capture ON for the same reason alpha does) from
        // open-beta (where the audience widens enough that ON-by-default
        // breaks the AOSP "off after support session" guidance).
        assertTrue(defaultDiagCaptureForStage("0.20.0-beta.1"))
        assertTrue(defaultDiagCaptureForStage("0.20.0-beta.3"))
    }

    @Test
    fun `release-candidate builds default OFF`() {
        assertFalse(defaultDiagCaptureForStage("1.0.0-rc.1"))
        assertFalse(defaultDiagCaptureForStage("1.0.0-rc.2"))
    }

    @Test
    fun `stable builds default OFF`() {
        assertFalse(defaultDiagCaptureForStage("1.0.0"))
        assertFalse(defaultDiagCaptureForStage("2.3.4"))
        assertFalse(defaultDiagCaptureForStage(""))
    }
}
