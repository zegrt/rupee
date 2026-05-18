package com.zegrt.rupee.diagnostics

import android.content.Context
import android.os.Bundle
import android.service.notification.StatusBarNotification
import com.zegrt.rupee.BuildConfig
import com.zegrt.rupee.data.local.dao.DumpOutcomeSnapshot
import com.zegrt.rupee.ingestion.IngestionResult
import java.io.File
import java.time.Instant

/**
 * Debug-only tool that captures every text-shaped value from every incoming
 * notification AND what Rupee's ingestion pipeline decided to do with it.
 *
 * Two-layer JSON per line:
 *  - the raw notification (`pkg`, `extras{}`, `actions[]` — the bank's text)
 *  - an `outcome{}` object describing the dispatch result (parser key, decision,
 *    amount/merchant extracted, candidate id, where the row landed, dedupe
 *    matches, BILL_DUE side-effects, gate-reject reasons)
 *
 * Format version 1 was raw-only. Version 2 adds the outcome block. Each line
 * carries `dumpFormatVersion` and `appVersion` so replay tooling can pick the
 * right schema. Old v1 lines stay parseable; v2 lines just have more fields.
 *
 * Writes to `getExternalFilesDir("notif-dumps")/dumps.jsonl`. The folder is
 * share-exportable so testers can email the file back without ADB. No-op in
 * release builds; the listener guard plus the BuildConfig.DEBUG check make it
 * impossible to write user notification data to disk in a release APK.
 */
object NotificationDumper {

    private const val DIR_NAME = "notif-dumps"
    private const val FILE_NAME = "dumps.jsonl"
    private const val DUMP_FORMAT_VERSION = 2
    // Cap raised from 2 MB → 4 MB to absorb the larger v2 lines. At ~1.5 KB
    // per line we hold roughly 2.5k notifications before rotation; rotation
    // drops the oldest half rather than truncating mid-line.
    private const val MAX_BYTES = 4 * 1024 * 1024

    fun isEnabled(): Boolean = BuildConfig.DEBUG

    /**
     * Write one line per notification — raw extras + ingestion outcome. The
     * caller (listener service / debug ingest) is responsible for producing
     * the [IngestionResult]; on the filtered path it constructs an
     * [IngestionResult.Filtered] synthetically. Doing both in one call site
     * means we never write a half-decorated line.
     */
    fun dump(context: Context, sbn: StatusBarNotification, outcome: IngestionResult) {
        if (!isEnabled()) return
        runCatching {
            val file = ensureFile(context) ?: return
            val json = buildJson(sbn, outcome)
            // Cap the file at MAX_BYTES — drop the oldest half if exceeded.
            // Line-aligned trim: find the first newline after the chop point
            // so the new head of file is always a complete JSON object.
            if (file.exists() && file.length() > MAX_BYTES) {
                val all = file.readBytes()
                val from = (all.size - MAX_BYTES / 2).coerceAtLeast(0)
                val newlineAt = (from..all.size).asSequence()
                    .firstOrNull { it < all.size && all[it].toInt() == '\n'.code }
                    ?: from
                file.writeBytes(all.sliceArray((newlineAt + 1).coerceAtMost(all.size) until all.size))
            }
            file.appendText("$json\n")
        }
    }

    fun directory(context: Context): File? =
        context.getExternalFilesDir(DIR_NAME)

    fun fileSize(context: Context): Long =
        directory(context)?.let { File(it, FILE_NAME) }?.takeIf(File::exists)?.length() ?: 0L

    fun clear(context: Context) {
        // Wipe the live dump plus any shared-copy artefacts (rupee-notif-dumps-*.jsonl,
        // rupee-notif-outcomes-*.jsonl).
        directory(context)?.listFiles()?.forEach { it.delete() }
    }

    /**
     * Pulls every `outcome.rawEventId` out of the live dump file. Phase 2a's
     * share path uses this to build the id list for the joined snapshot query.
     *
     * Regex over a full JSON parse — at 4 MB / ~2.5k lines this runs in tens
     * of milliseconds, vs the JSON parser's hundreds. Filtered outcomes have
     * no rawEventId; they're skipped automatically (the regex matches the
     * literal field, which they don't emit).
     *
     * Returned ids preserve dump order and are de-duplicated — a rawEventId
     * shows up at most once per ingest, but defensive dedupe protects against
     * future format changes (e.g. multi-pass re-ingest writing twice).
     */
    fun parseRawEventIdsFromDump(file: File): List<String> {
        if (!file.exists()) return emptyList()
        val seen = LinkedHashSet<String>()
        val pattern = Regex("\"rawEventId\":\"([^\"]+)\"")
        file.bufferedReader().useLines { lines ->
            for (line in lines) {
                pattern.find(line)?.groupValues?.getOrNull(1)?.let(seen::add)
            }
        }
        return seen.toList()
    }

    /**
     * Emit one JSON line per [DumpOutcomeSnapshot] to [outFile]. Format mirrors
     * the main dump's per-field encoding so the same downstream tooling can
     * parse both. Returns the file for share-intent wiring.
     *
     * Schema is keyed by `rawEventId` — pair with `dumps.jsonl` via that
     * field. Every line carries the same `dumpFormatVersion` / `appVersion`
     * pair as the main dump so replay scripts can pin schemas in lockstep.
     */
    fun writeOutcomesFile(outFile: File, snapshots: List<DumpOutcomeSnapshot>): File {
        outFile.parentFile?.mkdirs()
        outFile.bufferedWriter().use { writer ->
            for (snapshot in snapshots) {
                writer.write(snapshotJson(snapshot))
                writer.newLine()
            }
        }
        return outFile
    }

    private fun snapshotJson(snapshot: DumpOutcomeSnapshot): String = buildString {
        append('{')
        appendField("dumpFormatVersion", DUMP_FORMAT_VERSION.toString(), quote = false); append(',')
        appendField("appVersion", BuildConfig.VERSION_NAME); append(',')
        appendField("rawEventId", snapshot.rawEventId); append(',')
        appendField("parserKey", snapshot.parserKey); append(',')
        appendField("parserVersion", snapshot.parserVersion); append(',')
        appendField("transactionKind", snapshot.transactionKind); append(',')
        appendField("candidateId", snapshot.candidateId); append(',')
        appendField("candidateDecisionState", snapshot.candidateDecisionState); append(',')
        appendField("candidateDecisionReason", snapshot.candidateDecisionReason); append(',')
        appendField("inboxItemId", snapshot.inboxItemId); append(',')
        appendField("inboxDecisionState", snapshot.inboxDecisionState); append(',')
        appendField("inboxResolvedAt", snapshot.inboxResolvedAt); append(',')
        appendField("inboxLinkedCanonicalTxnId", snapshot.inboxLinkedCanonicalTxnId); append(',')
        appendField("canonicalTxnId", snapshot.canonicalTxnId); append(',')
        appendField("canonicalStatus", snapshot.canonicalStatus); append(',')
        appendField("canonicalType", snapshot.canonicalType); append(',')
        appendField("canonicalAmountMinor", snapshot.canonicalAmountMinor?.toString(), quote = false); append(',')
        appendField("canonicalMerchantName", snapshot.canonicalMerchantName); append(',')
        appendField("canonicalCategoryId", snapshot.canonicalCategoryId); append(',')
        appendField("canonicalNotes", snapshot.canonicalNotes); append(',')
        appendField("mergedIntoExistingTxnId", snapshot.mergedIntoExistingTxnId)
        append('}')
    }

    private fun ensureFile(context: Context): File? {
        val dir = directory(context) ?: return null
        if (!dir.exists()) dir.mkdirs()
        return File(dir, FILE_NAME)
    }

    /**
     * Build the JSON line. Public-internal for unit tests; takes pure data so
     * the listener-side callable wrapping can be tested without Android.
     */
    internal fun buildJson(sbn: StatusBarNotification, outcome: IngestionResult): String =
        buildString {
            append('{')
            appendField("dumpFormatVersion", DUMP_FORMAT_VERSION.toString(), quote = false); append(',')
            appendField("appVersion", BuildConfig.VERSION_NAME); append(',')
            appendField("when", Instant.now().toString()); append(',')
            appendField("pkg", sbn.packageName); append(',')
            appendField("postTime", sbn.postTime.toString()); append(',')
            appendField("flags", sbn.notification.flags.toString()); append(',')
            appendField("category", sbn.notification.category); append(',')
            appendField("tickerText", sbn.notification.tickerText?.toString()); append(',')
            append("\"actions\":[")
            sbn.notification.actions?.forEachIndexed { i, action ->
                if (i > 0) append(',')
                append('"').append(escape(action.title?.toString())).append('"')
            }
            append("],")
            append("\"extras\":{")
            val extras = sbn.notification.extras ?: Bundle()
            var first = true
            for (key in extras.keySet()) {
                val value = extras.get(key)
                val rendered = renderExtraValue(value) ?: continue
                if (!first) append(',')
                first = false
                appendField(key, rendered)
            }
            append('}')
            append(',')
            append("\"outcome\":")
            append(outcomeJson(outcome))
            append('}')
        }

    private fun outcomeJson(outcome: IngestionResult): String = buildString {
        append('{')
        when (outcome) {
            is IngestionResult.Filtered -> {
                appendField("kind", "filtered"); append(',')
                appendField("reason", outcome.reason.name)
            }
            is IngestionResult.GateRejected -> {
                appendField("kind", "gate_rejected"); append(',')
                appendField("rawEventId", outcome.rawEventId); append(',')
                appendField("gateReason", outcome.gateReason); append(',')
                appendField("matched", outcome.matched)
            }
            is IngestionResult.Ingested -> {
                appendField("kind", "ingested"); append(',')
                appendField("rawEventId", outcome.rawEventId); append(',')
                appendField("parsedSignalId", outcome.parsedSignalId); append(',')
                appendField("candidateId", outcome.candidateId); append(',')
                appendField("parserKey", outcome.parserKey); append(',')
                appendField("parserVersion", outcome.parserVersion); append(',')
                appendField("providerHint", outcome.providerHint); append(',')
                appendField("transactionKind", outcome.transactionKind.name); append(',')
                appendField("candidateType", outcome.candidateType.name); append(',')
                appendField("amountMinor", outcome.amountMinor?.toString(), quote = false); append(',')
                appendField("currencyCode", outcome.currencyCode); append(',')
                appendField("merchantRaw", outcome.merchantRaw); append(',')
                appendField("toEntityName", outcome.toEntityName); append(',')
                appendField("mode", outcome.mode?.name); append(',')
                appendField("maskedDigits", outcome.maskedDigits); append(',')
                appendField("parseConfidence", outcome.parseConfidence.toString(), quote = false); append(',')
                appendField("networkReferenceId", outcome.networkReferenceId); append(',')
                appendField("networkReferenceType", outcome.networkReferenceType); append(',')
                appendField("dueDateIso", outcome.dueDateIso); append(',')
                appendField("confidenceTier", outcome.confidenceTier?.name); append(',')
                appendField("decisionState", outcome.decisionState.name); append(',')
                appendField("decisionReason", outcome.decisionReason.name); append(',')
                appendField("trustRuleMatched", outcome.trustRuleMatched.toString(), quote = false); append(',')
                appendField("dedupedAgainstCandidateId", outcome.dedupedAgainstCandidateId); append(',')
                appendField("dedupedAgainstCanonicalTxnId", outcome.dedupedAgainstCanonicalTxnId); append(',')
                appendField("inboxItemId", outcome.inboxItemId); append(',')
                appendField("canonicalTransactionId", outcome.canonicalTransactionId); append(',')
                appendField("billDueAppliedToCardId", outcome.billDueAppliedToCardId)
            }
        }
        append('}')
    }

    private fun renderExtraValue(value: Any?): String? = when (value) {
        null -> null
        is CharSequence -> value.toString().takeIf(String::isNotBlank)
        is Array<*> -> value
            .mapNotNull { (it as? CharSequence)?.toString()?.takeIf(String::isNotBlank) }
            .takeIf { it.isNotEmpty() }
            ?.joinToString(" | ")
        is Boolean, is Number -> value.toString()
        // Bitmaps, Parcelables, PendingIntents — skip; they'd blow up the dump
        // and aren't text content anyway.
        else -> null
    }

    private fun StringBuilder.appendField(key: String, value: String?, quote: Boolean = true) {
        append('"').append(escape(key)).append("\":")
        if (value == null) {
            append("null")
        } else if (quote) {
            append('"').append(escape(value)).append('"')
        } else {
            // Bare value for numbers/booleans — let the consumer parse natively.
            append(value)
        }
    }

    private fun escape(s: String?): String {
        if (s == null) return ""
        val out = StringBuilder(s.length)
        for (c in s) {
            when (c) {
                '\\' -> out.append("\\\\")
                '"' -> out.append("\\\"")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                else -> if (c < ' ') out.append(String.format("\\u%04x", c.code)) else out.append(c)
            }
        }
        return out.toString()
    }
}
