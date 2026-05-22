package com.zegrt.rupee.ingestion.replay

import com.zegrt.rupee.data.local.entity.CandidateDecisionState
import com.zegrt.rupee.data.local.entity.ParsedTransactionKind
import com.zegrt.rupee.data.local.entity.RawCaptureEventEntity
import com.zegrt.rupee.data.local.entity.RawCaptureIngestionStatus
import com.zegrt.rupee.data.local.entity.RawCaptureSourceType
import com.zegrt.rupee.data.local.entity.SyncStatus
import com.zegrt.rupee.ingestion.NotificationDecisionEngine
import com.zegrt.rupee.ingestion.NotificationExtractor
import com.zegrt.rupee.ingestion.NotificationParserRegistry
import com.zegrt.rupee.ingestion.TransactionalGate
import org.json.JSONObject
import java.io.File

/**
 * T2 — dump-replay regression harness.
 *
 * Reads a production dump file, replays each captured notification through
 * the *pure* parts of the ingestion pipeline (gate → parser registry →
 * decision engine), and counts what the *current* code would have done.
 * Compare those counts to a baseline snapshot — if the ingested rate drops
 * more than the configured drift tolerance, a PR has regressed the parser.
 *
 * What the harness does NOT cover:
 *  - The Room/DAO write boundary. That's T1's instrumented suite. Replay
 *    skips dedupe (it would need a real DB) and never writes anywhere.
 *  - Listener-level filters (group_summary, self_pkg, empty_body,
 *    raw_duplicate). Those happened in production *before* the body
 *    reached normalizeLocked and are recorded as `outcome.kind = filtered`
 *    in the dump. Replay skips those lines — they wouldn't have been
 *    passed to the gate anyway.
 *
 * Drift policy is the caller's choice (see DumpReplayTest). The simplest
 * shape: assert that the ingested count today ≥ baseline. Improvements
 * (we added a verb, more bodies ingest) are fine; regressions (we lost a
 * shape, fewer ingest) fail the build.
 *
 * Lives in test source set instead of androidTest because the harness is
 * pure Kotlin (no Room, no Android framework). Runs as a regular JVM unit
 * test in seconds — no emulator required, CI-friendly.
 */
object DumpReplayHarness {

    private val parserRegistry = NotificationParserRegistry.default()
    private val decisionEngine = NotificationDecisionEngine()

    /** What replaying a single dump file produces. */
    data class ReplayResult(
        val dumpName: String,
        val totalLines: Int,
        /** Lines we skipped — listener-filtered in production; replay does not
         *  re-evaluate them. */
        val skippedListenerFiltered: Int,
        /** Lines whose body the current gate rejects. */
        val gateRejected: Int,
        /** Real parser hit, candidate would be AUTO_CREATED. */
        val autoCreated: Int,
        /** Real parser hit, candidate would be INBOX_PENDING. */
        val inboxPending: Int,
        /** Real parser hit, candidate would be IGNORED (low confidence,
         *  missing amount, etc.). */
        val ignored: Int,
        /** Lines that threw during replay — should be 0 in a healthy build. */
        val replayErrors: Int,
        /** Per-parser-key counts among the lines that reached a parser. */
        val parserKeyCounts: Map<String, Int>,
    ) {
        /** Sum of all non-skipped, non-error outcomes. */
        val routedTotal: Int = gateRejected + autoCreated + inboxPending + ignored

        /** "Reached the parser layer" — gate accepted. */
        val accepted: Int = autoCreated + inboxPending + ignored
    }

    fun replay(dumpFile: File): ReplayResult {
        var total = 0
        var skipped = 0
        var gateRej = 0
        var auto = 0
        var inbox = 0
        var ignored = 0
        var errors = 0
        val parserCounts = mutableMapOf<String, Int>()

        dumpFile.bufferedReader().forEachLine { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) return@forEachLine
            total++

            runCatching {
                val obj = JSONObject(line)
                val outcome = obj.optJSONObject("outcome")
                val kind = outcome?.optString("kind")

                // Skip listener-pre-filtered lines (group_summary, self_pkg,
                // empty_body, raw_duplicate, normalizer_unavailable). The body
                // never reached normalizeLocked in production, so replay has
                // nothing to assert on. INGEST_FAILED stays — those bodies DID
                // reach normalize and crashed; we want to know if replay
                // crashes too.
                if (kind == "filtered") {
                    val reason = outcome.optString("reason")
                    if (reason != "INGEST_FAILED") {
                        skipped++
                        return@forEachLine
                    }
                }

                // Rebuild the same combinedBody NotificationExtractor would have
                // produced from this notification's extras. Dumper stores all
                // values as strings, so we map straight across.
                val extrasJson = obj.optJSONObject("extras") ?: JSONObject()
                val extras: Map<String, Any?> = buildMap {
                    extrasJson.keys().forEach { k -> put(k, extrasJson.opt(k)) }
                }
                val flags = obj.optString("flags").toIntOrNull() ?: 0
                val ticker = obj.optString("tickerText").takeIf { !obj.isNull("tickerText") }
                val extracted = NotificationExtractor.extract(
                    extras = extras,
                    flags = flags,
                    ticker = ticker,
                    messages = emptyList(),
                    actionLabels = emptyList(),
                )
                val combinedBody = extracted.combinedBody
                if (combinedBody.isBlank()) {
                    skipped++
                    return@forEachLine
                }

                val gateDecision = TransactionalGate.evaluate(combinedBody)
                if (gateDecision is TransactionalGate.Decision.Reject) {
                    gateRej++
                    return@forEachLine
                }

                // Synthetic raw event so the parser registry can run. Values
                // mirror what NotificationSignalNormalizer would have built;
                // most parsers don't read these fields beyond body / pkg /
                // userId, but we wire them all so a parser that adds a new
                // input later doesn't quietly NPE.
                val pkg = obj.optString("pkg").takeIf { it.isNotEmpty() && !obj.isNull("pkg") }
                val rawEvent = RawCaptureEventEntity(
                    id = "replay-${total}",
                    userId = "replay-user",
                    sourceType = RawCaptureSourceType.NOTIFICATION,
                    sourceAppPackage = pkg,
                    title = extracted.title,
                    body = combinedBody,
                    receivedAt = "2026-01-01T00:00:00Z",
                    deviceEventTime = null,
                    hashFingerprint = "replay-${total}",
                    ingestionStatus = RawCaptureIngestionStatus.CAPTURED,
                    createdAt = "2026-01-01T00:00:00Z",
                    updatedAt = "2026-01-01T00:00:00Z",
                    syncStatus = SyncStatus.LOCAL_ONLY,
                )

                val parseResult = parserRegistry.parse(rawEvent)
                parserCounts.merge(parseResult.parserKey, 1, Int::plus)

                // Replay can't run the dedupe engine (no DB), so we route
                // every body through the decision engine and label by its
                // verdict. In production a dedupe-hit would flip the
                // decisionState to IGNORED, but for "did the parser still
                // recognise this body?" the decision engine's verdict is
                // sufficient.
                val decision = decisionEngine.decide(parseResult)
                when (decision.decisionState) {
                    CandidateDecisionState.AUTO_CREATED -> auto++
                    CandidateDecisionState.INBOX_PENDING -> inbox++
                    CandidateDecisionState.IGNORED -> ignored++
                    CandidateDecisionState.USER_CONFIRMED -> {
                        // Decision engine never returns this — but defend.
                        inbox++
                    }
                }
                // Sentinel for kinds the decision engine routes oddly.
                if (parseResult.transactionKind == ParsedTransactionKind.UNKNOWN &&
                    parseResult.amountMinor == null
                ) {
                    // already counted above; no double-count.
                }
            }.onFailure {
                errors++
            }
        }

        return ReplayResult(
            dumpName = dumpFile.name,
            totalLines = total,
            skippedListenerFiltered = skipped,
            gateRejected = gateRej,
            autoCreated = auto,
            inboxPending = inbox,
            ignored = ignored,
            replayErrors = errors,
            parserKeyCounts = parserCounts.toMap(),
        )
    }

    /**
     * Compare a current run against a baseline. Returns null when the run is
     * within tolerance; otherwise a human-readable failure message.
     *
     * Asymmetric drift policy:
     * - `acceptedDropTolerance` controls how many fewer "accepted by gate"
     *   bodies the new run can have vs baseline. Improvements (more accepted)
     *   are always allowed.
     * - `autoCreatedDropTolerance` controls how many fewer `AUTO_CREATED`
     *   tier landings the new run can have. The S1.3 tier-promotion work
     *   shifted bodies from INBOX_PENDING → AUTO_CREATED on purpose;
     *   tightening this catches accidental demotions back to INBOX.
     *
     * Defaults are deliberately tight (tolerance 1 / 0) — the corpus is
     * settled as of Sprint 2's DUMP-REPLAY-REBASELINE pass. Loosen explicitly
     * when an intended scoring change moves bodies around.
     */
    fun compareToBaseline(
        current: ReplayResult,
        baseline: Baseline,
        acceptedDropTolerance: Int = 1,
        autoCreatedDropTolerance: Int = 0,
        errorTolerance: Int = 0,
    ): String? {
        val problems = mutableListOf<String>()
        if (current.replayErrors > errorTolerance) {
            problems += "replay raised ${current.replayErrors} exceptions (baseline ${baseline.replayErrors}, tolerance $errorTolerance)"
        }
        val acceptedDrop = baseline.accepted - current.accepted
        if (acceptedDrop > acceptedDropTolerance) {
            problems += "gate-accepted bodies dropped: baseline=${baseline.accepted}, current=${current.accepted}, drop=$acceptedDrop (tolerance $acceptedDropTolerance)"
        }
        val autoDrop = baseline.autoCreated - current.autoCreated
        if (autoDrop > autoCreatedDropTolerance) {
            problems += "AUTO_CREATED bodies dropped: baseline=${baseline.autoCreated}, current=${current.autoCreated}, drop=$autoDrop (tolerance $autoCreatedDropTolerance)"
        }
        return if (problems.isEmpty()) null else problems.joinToString("; ")
    }

    /**
     * Frozen baseline shape — what we expect each dump's replay to produce
     * after a known-good build. Stored as constants in DumpReplayTest, not
     * a separate JSON file: keeps the assertion + the value in the same
     * place so they don't drift independently.
     */
    data class Baseline(
        val accepted: Int,
        val autoCreated: Int,
        val replayErrors: Int,
    )
}
