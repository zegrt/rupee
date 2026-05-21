package com.zegrt.rupee.diagnostics

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.zegrt.rupee.data.local.entity.AccountEntity
import com.zegrt.rupee.data.local.entity.CanonicalTransactionEntity
import com.zegrt.rupee.data.local.entity.CategoryEntity
import com.zegrt.rupee.data.local.entity.CreditCardEntity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import org.json.JSONArray
import org.json.JSONObject

/**
 * Snapshot bundle that LedgerExporter writes out. Resolves transaction
 * foreign keys (accountId / creditCardId / categoryId) against the lookup
 * tables so the exported file shows readable names, not IDs.
 *
 * Owned by the repository (see `LocalFinanceRepository.getLedgerExportSnapshot`).
 */
data class LedgerExportSnapshot(
    val transactions: List<CanonicalTransactionEntity>,
    val accountsById: Map<String, AccountEntity>,
    val cardsById: Map<String, CreditCardEntity>,
    val categoriesById: Map<String, CategoryEntity>,
)

/**
 * Writes a user-initiated ledger export to a CSV or JSON file in the
 * app's external files directory under `ledger-exports/`, returns a
 * FileProvider URI suitable for an ACTION_SEND share intent.
 *
 * Why a dedicated exporter (rather than reusing `NotificationDumper`):
 * - Output is user-facing, not diagnostic. Stored in a separate
 *   subfolder (`ledger-exports/`) so a user sharing their ledger never
 *   accidentally attaches debug notification dumps.
 * - CSV is required by the PRD §14 export feature. JSON is the same
 *   data structured for downstream tooling.
 * - Stays a pure-JVM file writer; suspension + dispatch is the caller's
 *   problem.
 */
object LedgerExporter {

    private const val DIR_NAME = "ledger-exports"

    enum class Format(val extension: String, val mime: String) {
        CSV("csv", "text/csv"),
        JSON("json", "application/json"),
    }

    /**
     * Write [snapshot] to a new file in `<external files>/ledger-exports/`
     * and return its FileProvider URI. Wipes prior export artefacts first
     * so the folder doesn't accumulate one file per share over the app's
     * lifetime.
     */
    fun export(
        context: Context,
        snapshot: LedgerExportSnapshot,
        format: Format,
        versionName: String,
    ): Uri {
        val dir = File(context.getExternalFilesDir(null), DIR_NAME).apply { mkdirs() }
        // Tidy stale exports so the directory has at most one file per
        // format. The user can re-share the live one; older ones are
        // off-device by now in the recipient's downloads.
        dir.listFiles { _, name -> name.startsWith("rupee-ledger-") }?.forEach { it.delete() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
            .apply { timeZone = TimeZone.getDefault() }
            .format(Date())
        val file = File(dir, "rupee-ledger-$versionName-$stamp.${format.extension}")
        when (format) {
            Format.CSV -> writeCsv(file, snapshot)
            Format.JSON -> writeJson(file, snapshot)
        }
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
    }

    private fun writeCsv(file: File, snapshot: LedgerExportSnapshot) {
        // Excel-friendly CSV: double-quote every field, escape internal
        // quotes by doubling. Header row first.
        val sb = StringBuilder()
        sb.appendLine("occurredAt,type,status,amountMinor,currency,merchant,category,account,card,mode,notes,id")
        for (t in snapshot.transactions) {
            val account = t.accountId?.let { snapshot.accountsById[it]?.displayName }
            val card = t.creditCardId?.let { snapshot.cardsById[it]?.displayName }
            val category = t.categoryId?.let { snapshot.categoriesById[it]?.name }
            val row = listOf(
                t.occurredAt,
                t.type.name,
                t.status.name,
                t.amountMinor.toString(),
                t.currencyCode ?: "",
                t.merchantName ?: "",
                category ?: "",
                account ?: "",
                card ?: "",
                t.mode?.name ?: "",
                t.notes ?: "",
                t.id,
            )
            sb.appendLine(row.joinToString(",") { csvField(it) })
        }
        file.writeText(sb.toString())
    }

    private fun csvField(value: String): String {
        // Always quote — safer than guessing which fields contain commas /
        // newlines / quotes. Doubling internal quotes follows RFC 4180.
        val escaped = value.replace("\"", "\"\"")
        return "\"$escaped\""
    }

    private fun writeJson(file: File, snapshot: LedgerExportSnapshot) {
        val rows = JSONArray()
        for (t in snapshot.transactions) {
            val account = t.accountId?.let { snapshot.accountsById[it]?.displayName }
            val card = t.creditCardId?.let { snapshot.cardsById[it]?.displayName }
            val category = t.categoryId?.let { snapshot.categoriesById[it]?.name }
            val obj = JSONObject().apply {
                put("id", t.id)
                put("occurredAt", t.occurredAt)
                put("type", t.type.name)
                put("status", t.status.name)
                put("amountMinor", t.amountMinor)
                put("currency", t.currencyCode ?: JSONObject.NULL)
                put("merchant", t.merchantName ?: JSONObject.NULL)
                put("category", category ?: JSONObject.NULL)
                put("account", account ?: JSONObject.NULL)
                put("card", card ?: JSONObject.NULL)
                put("mode", t.mode?.name ?: JSONObject.NULL)
                put("notes", t.notes ?: JSONObject.NULL)
            }
            rows.put(obj)
        }
        val envelope = JSONObject().apply {
            put("schemaVersion", 1)
            put("exportedAt", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(Date()))
            put("transactionCount", snapshot.transactions.size)
            put("transactions", rows)
        }
        file.writeText(envelope.toString(2))
    }
}
