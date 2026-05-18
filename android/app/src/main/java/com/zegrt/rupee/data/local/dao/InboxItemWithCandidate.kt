package com.zegrt.rupee.data.local.dao

import androidx.room.Embedded
import androidx.room.Relation
import com.zegrt.rupee.data.local.entity.InboxItemEntity
import com.zegrt.rupee.data.local.entity.TransactionCandidateEntity

/**
 * Inbox row joined to its underlying candidate at the DB.
 *
 * Replaces the in-memory join the Home dashboard previously did across
 * `observePendingInboxItems` + `observeRecentTransactionCandidates(limit=20)`.
 * That setup rendered husks (empty merchant, "—" amount) whenever the
 * candidate referenced by a pending inbox row had rotated past the 20-row
 * candidate window — common after a burst of marketing / OTP / promo
 * notifications, since `writeGateRejectedSignal` also writes candidate rows.
 *
 * With `@Relation`, Room hydrates the candidate for each inbox row in the
 * same query pass; husks become structurally impossible. `candidate` is
 * nullable so an orphan inbox row (shouldn't happen in normal flow — no
 * code deletes candidates today, but H4 in the gravedigging audit notes
 * the FK constraints aren't enforced) still surfaces with the inbox
 * fields populated, letting the UI render a sane fallback rather than
 * crashing.
 */
data class InboxItemWithCandidate(
    @Embedded val inbox: InboxItemEntity,
    @Relation(
        parentColumn = "transactionCandidateId",
        entityColumn = "id",
    )
    val candidate: TransactionCandidateEntity?,
)
