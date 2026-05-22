package com.zegrt.rupee.ingestion.replay

import com.zegrt.rupee.ingestion.replay.DumpReplayHarness.Baseline
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/**
 * T2 — runs the replay harness against every committed dump and asserts
 * the current code's behaviour doesn't drop below a frozen baseline.
 *
 * When you intentionally improve the gate or parsers (more bodies route to
 * a real parser, fewer get NO_TRANSACTIONAL_VERB'd), the `accepted` number
 * goes UP from its baseline — that's allowed. When a PR regresses (fewer
 * bodies accepted, or any replay-time exception), the test fails. Update
 * the baseline in the same PR if the improvement is intentional.
 *
 * The dumps directory is read via a relative path from the test working
 * directory (`android/app/`). Tests resolve `../../dumps/...`.
 *
 * To add a new dump: drop the file into `dumps/`, run this test once with
 * a placeholder baseline, copy the assertion-failure message's "current="
 * number into the new baseline, commit.
 */
class DumpReplayTest {

    @Test
    fun `replay v0_13_3 Nothing-A015 dump meets baseline`() {
        val result = DumpReplayHarness.replay(dump("rupee-notif-dumps-0.13.3-Nothing-A015-20260513-140757.jsonl"))
        // Print so a failure leaves the diff in the test report.
        println("REPLAY ${result.dumpName}: $result")
        assertNull(
            DumpReplayHarness.compareToBaseline(result, BASELINE_V0_13_3),
        )
    }

    @Test
    fun `replay v0_14_0 Nothing-A015 dump meets baseline`() {
        val result = DumpReplayHarness.replay(dump("rupee-notif-dumps-0.14.0-Nothing-A015-20260519-202444.jsonl"))
        println("REPLAY ${result.dumpName}: $result")
        assertNull(
            DumpReplayHarness.compareToBaseline(result, BASELINE_V0_14_0),
        )
    }

    private fun dump(name: String): File {
        val direct = File("../../dumps/$name")
        if (direct.exists()) return direct
        // Some IDE runners use the project root as the cwd.
        val fromRoot = File("dumps/$name")
        if (fromRoot.exists()) return fromRoot
        error("dump $name not found; cwd=${File(".").absolutePath}")
    }

    companion object {
        /**
         * Baselines rebaselined 2026-05-23 against post-Sprint-2 main
         * (Sprint 3 DUMP-REPLAY-REBASELINE pass). `accepted` is the union
         * of AUTO_CREATED + INBOX_PENDING + IGNORED — anything that
         * reached the parser layer. `autoCreated` is broken out
         * separately because Sprint 1's S1.3 pilot + Sprint 2's S1.3
         * (rest) deliberately promoted bodies from INBOX_PENDING into
         * AUTO_CREATED. We want a regression to that promotion to fail.
         *
         * Default tolerances live in `compareToBaseline`: accepted drift
         * ≤1, autoCreated drift = 0, replay errors = 0. Improvements
         * (more accepted, more auto-created) always pass.
         *
         * v0.13.3 dump: 504 total → 472 gate-rejected, 32 accepted
         *   (6 AUTO_CREATED, 15 INBOX_PENDING, 11 IGNORED).
         *   Was 0/21/11 pre-Sprint-2 — 6 bodies moved from inbox to auto
         *   thanks to the CRED/ICICI HIGH-tier promotions in S1.3-rest.
         * v0.14.0 dump: 916 total → 171 listener-filtered (skipped),
         *   731 gate-rejected, 14 accepted (5 AUTO_CREATED,
         *   6 INBOX_PENDING, 3 IGNORED). Same shift: was 0/11/3
         *   pre-Sprint-2; 5 bodies promoted by the ICICI/CRED tier
         *   work.
         */
        private val BASELINE_V0_13_3 = Baseline(
            accepted = 32,
            autoCreated = 6,
            replayErrors = 0,
        )
        private val BASELINE_V0_14_0 = Baseline(
            accepted = 14,
            autoCreated = 5,
            replayErrors = 0,
        )
    }
}
