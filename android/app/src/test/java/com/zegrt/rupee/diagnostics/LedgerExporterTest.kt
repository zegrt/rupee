package com.zegrt.rupee.diagnostics

import com.zegrt.rupee.data.local.entity.AccountEntity
import com.zegrt.rupee.data.local.entity.AccountType
import com.zegrt.rupee.data.local.entity.CanonicalTransactionEntity
import com.zegrt.rupee.data.local.entity.CanonicalTransactionStatus
import com.zegrt.rupee.data.local.entity.CanonicalTransactionType
import com.zegrt.rupee.data.local.entity.CategoryEntity
import com.zegrt.rupee.data.local.entity.Mode
import com.zegrt.rupee.data.local.entity.SyncStatus
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit coverage for the pure-string CSV/JSON rendering in
 * `LedgerExporter`. Doesn't touch FileProvider / Context — those are
 * exercised manually on-device. The risk this guards against is the
 * silent failure modes of the format encoders:
 *
 *  - CSV: a quote-doubling regression makes Excel mis-parse columns
 *    forever afterwards
 *  - CSV: an extra comma in a non-quoted field shifts every downstream
 *    column on that row
 *  - JSON: an envelope-shape regression breaks downstream tools that
 *    pin to `schemaVersion: 1`
 */
class LedgerExporterTest {

    private fun txn(
        id: String = "txn-1",
        merchant: String? = "Swiggy",
        amountMinor: Long = 24500,
        notes: String? = null,
        accountId: String? = "acc-1",
        categoryId: String? = "cat-food",
    ) = CanonicalTransactionEntity(
        id = id,
        userId = "local-user",
        type = CanonicalTransactionType.EXPENSE,
        status = CanonicalTransactionStatus.CONFIRMED,
        amountMinor = amountMinor,
        currencyCode = "INR",
        accountId = accountId,
        merchantName = merchant,
        categoryId = categoryId,
        mode = Mode.UPI,
        notes = notes,
        occurredAt = "2026-05-22T14:22:00Z",
        createdBy = "test",
        createdAt = "2026-05-22T14:22:00Z",
        updatedAt = "2026-05-22T14:22:00Z",
        syncStatus = SyncStatus.LOCAL_ONLY,
    )

    private fun account(id: String = "acc-1", name: String = "ICICI Savings") = AccountEntity(
        id = id,
        userId = "local-user",
        accountType = AccountType.BANK,
        displayName = name,
        currencyCode = "INR",
        createdAt = "2026-05-22T14:22:00Z",
        updatedAt = "2026-05-22T14:22:00Z",
        syncStatus = SyncStatus.LOCAL_ONLY,
    )

    private fun category(id: String = "cat-food", name: String = "Food") = CategoryEntity(
        id = id,
        userId = "local-user",
        name = name,
        createdAt = "2026-05-22T14:22:00Z",
        updatedAt = "2026-05-22T14:22:00Z",
        syncStatus = SyncStatus.LOCAL_ONLY,
    )

    @Test
    fun csv_emitsHeaderAndOneRow() {
        val snap = LedgerExportSnapshot(
            transactions = listOf(txn()),
            accountsById = mapOf("acc-1" to account()),
            cardsById = emptyMap(),
            categoriesById = mapOf("cat-food" to category()),
        )
        val csv = LedgerExporter.toCsv(snap)
        val lines = csv.trim().lines()
        assertEquals(2, lines.size)
        assertEquals(
            "occurredAt,type,status,amountMinor,currency,merchant,category,account,card,mode,notes,id",
            lines[0],
        )
        // Row should resolve account/category names and quote every field.
        assertTrue(
            "row should contain quoted merchant + resolved account name; was: ${lines[1]}",
            lines[1].contains("\"Swiggy\"") &&
                lines[1].contains("\"Food\"") &&
                lines[1].contains("\"ICICI Savings\""),
        )
    }

    @Test
    fun csv_doublesInternalQuotes_perRFC4180() {
        val snap = LedgerExportSnapshot(
            transactions = listOf(
                txn(merchant = "Joe's \"Diner\"", notes = "with \"quotes\""),
            ),
            accountsById = emptyMap(),
            cardsById = emptyMap(),
            categoriesById = emptyMap(),
        )
        val csv = LedgerExporter.toCsv(snap)
        // Field has a quote → must be wrapped in quotes AND internal quote doubled.
        assertTrue(
            "expected RFC 4180 quote-doubling, got: $csv",
            csv.contains("\"Joe's \"\"Diner\"\"\"") &&
                csv.contains("\"with \"\"quotes\"\"\""),
        )
    }

    @Test
    fun csv_emptyFkResolutions_renderAsBlank() {
        // Transaction with no accountId / no categoryId — lookups return
        // null, columns must be empty strings ("") not literal "null".
        val snap = LedgerExportSnapshot(
            transactions = listOf(txn(accountId = null, categoryId = null)),
            accountsById = emptyMap(),
            cardsById = emptyMap(),
            categoriesById = emptyMap(),
        )
        val csv = LedgerExporter.toCsv(snap)
        // Account + category columns are empty quoted fields.
        assertTrue("expected empty quoted account column", csv.contains("\"\""))
        assertTrue(
            "expected no 'null' literal in CSV body, got: $csv",
            !csv.contains("\"null\""),
        )
    }

    @Test
    fun json_envelopeShape() {
        val snap = LedgerExportSnapshot(
            transactions = listOf(txn(id = "a"), txn(id = "b", merchant = "Uber")),
            accountsById = mapOf("acc-1" to account()),
            cardsById = emptyMap(),
            categoriesById = mapOf("cat-food" to category()),
        )
        val parsed = JSONObject(LedgerExporter.toJson(snap))
        assertEquals(1, parsed.getInt("schemaVersion"))
        assertEquals(2, parsed.getInt("transactionCount"))
        assertTrue("envelope has exportedAt", parsed.has("exportedAt"))
        val rows = parsed.getJSONArray("transactions")
        assertEquals(2, rows.length())
        val first = rows.getJSONObject(0)
        assertEquals("a", first.getString("id"))
        assertEquals("Swiggy", first.getString("merchant"))
        assertEquals("Food", first.getString("category"))
        assertEquals("ICICI Savings", first.getString("account"))
        assertEquals("UPI", first.getString("mode"))
        assertEquals(24500L, first.getLong("amountMinor"))
    }

    @Test
    fun json_nullsRoundTripAsJsonNull() {
        val snap = LedgerExportSnapshot(
            transactions = listOf(txn(merchant = null, notes = null, accountId = null, categoryId = null)),
            accountsById = emptyMap(),
            cardsById = emptyMap(),
            categoriesById = emptyMap(),
        )
        val parsed = JSONObject(LedgerExporter.toJson(snap))
        val row = parsed.getJSONArray("transactions").getJSONObject(0)
        assertTrue("merchant should be JSON null", row.isNull("merchant"))
        assertTrue("category should be JSON null", row.isNull("category"))
        assertTrue("account should be JSON null", row.isNull("account"))
        assertTrue("notes should be JSON null", row.isNull("notes"))
    }

    @Test
    fun csv_emptySnapshot_yieldsHeaderOnly() {
        // The empty-ledger guard in SettingsViewModel prevents the export
        // path from reaching here in production, but verify the format
        // handles it gracefully if a future caller forgets the guard.
        val csv = LedgerExporter.toCsv(
            LedgerExportSnapshot(emptyList(), emptyMap(), emptyMap(), emptyMap()),
        )
        assertEquals(1, csv.trim().lines().size)
    }
}
