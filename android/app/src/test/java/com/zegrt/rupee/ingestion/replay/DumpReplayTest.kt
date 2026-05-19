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
         * Baselines captured 2026-05-20 against the t2-dump-replay-harness
         * branch (post-v0.14.1, with the H4 ordering fix + v0.14.0 vocab
         * adds + single-X masked-digit regex). `accepted` is the number of
         * gate-accepted bodies (the union of AUTO_CREATED + INBOX_PENDING +
         * IGNORED — i.e. anything that reached the parser layer).
         *
         * A drop of more than the default tolerance (5) from these counts
         * on a future PR signals a regression. Increases are always
         * allowed; bump the baseline in the same PR if a real improvement.
         *
         * v0.13.3 dump: 504 total → 472 gate-rejected, 32 accepted
         *   (0 auto-created, 21 inbox-pending, 11 ignored).
         * v0.14.0 dump: 916 total → 171 skipped (listener-filtered in
         *   prod), 731 gate-rejected, 14 accepted (0 auto-created,
         *   11 inbox-pending, 3 ignored). The 11 INBOX_PENDING here are
         *   the bodies the v0.14.0 H4 FK regression was eating; with
         *   v0.14.1's write-order fix they now route to Inbox properly.
         */
        private val BASELINE_V0_13_3 = Baseline(accepted = 32, replayErrors = 0)
        private val BASELINE_V0_14_0 = Baseline(accepted = 14, replayErrors = 0)
    }
}
