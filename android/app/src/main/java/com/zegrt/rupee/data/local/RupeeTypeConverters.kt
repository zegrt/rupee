package com.zegrt.rupee.data.local

import androidx.room.TypeConverter
import com.zegrt.rupee.data.local.entity.AccountType
import com.zegrt.rupee.data.local.entity.BudgetType
import com.zegrt.rupee.data.local.entity.CandidateDecisionReason
import com.zegrt.rupee.data.local.entity.CandidateDecisionState
import com.zegrt.rupee.data.local.entity.CanonicalTransactionStatus
import com.zegrt.rupee.data.local.entity.CanonicalTransactionType
import com.zegrt.rupee.data.local.entity.ConfidenceTier
import com.zegrt.rupee.data.local.entity.InboxDecisionState
import com.zegrt.rupee.data.local.entity.InboxReasonCode
import com.zegrt.rupee.data.local.entity.Mode
import com.zegrt.rupee.data.local.entity.ParsedTransactionKind
import com.zegrt.rupee.data.local.entity.RawCaptureIngestionStatus
import com.zegrt.rupee.data.local.entity.RawCaptureSourceType
import com.zegrt.rupee.data.local.entity.SyncStatus
import com.zegrt.rupee.data.local.entity.TransactionCandidateType

class RupeeTypeConverters {
    @TypeConverter
    fun fromAccountType(value: AccountType?): String? = value?.name

    @TypeConverter
    fun toAccountType(value: String?): AccountType? = value?.let(AccountType::valueOf)

    @TypeConverter
    fun fromBudgetType(value: BudgetType?): String? = value?.name

    @TypeConverter
    fun toBudgetType(value: String?): BudgetType? = value?.let(BudgetType::valueOf)

    @TypeConverter
    fun fromTransactionType(value: CanonicalTransactionType?): String? = value?.name

    @TypeConverter
    fun toTransactionType(value: String?): CanonicalTransactionType? =
        value?.let(CanonicalTransactionType::valueOf)

    @TypeConverter
    fun fromTransactionStatus(value: CanonicalTransactionStatus?): String? = value?.name

    @TypeConverter
    fun toTransactionStatus(value: String?): CanonicalTransactionStatus? =
        value?.let(CanonicalTransactionStatus::valueOf)

    @TypeConverter
    fun fromConfidenceTier(value: ConfidenceTier?): String? = value?.name

    @TypeConverter
    fun toConfidenceTier(value: String?): ConfidenceTier? = value?.let(ConfidenceTier::valueOf)

    @TypeConverter
    fun fromInboxDecisionState(value: InboxDecisionState?): String? = value?.name

    @TypeConverter
    fun toInboxDecisionState(value: String?): InboxDecisionState? =
        value?.let(InboxDecisionState::valueOf)

    @TypeConverter
    fun fromInboxReasonCode(value: InboxReasonCode?): String? = value?.name

    @TypeConverter
    fun toInboxReasonCode(value: String?): InboxReasonCode? = value?.let(InboxReasonCode::valueOf)

    @TypeConverter
    fun fromMode(value: Mode?): String? = value?.name

    @TypeConverter
    fun toMode(value: String?): Mode? = value?.let(Mode::valueOf)

    @TypeConverter
    fun fromRawCaptureSourceType(value: RawCaptureSourceType?): String? = value?.name

    @TypeConverter
    fun toRawCaptureSourceType(value: String?): RawCaptureSourceType? =
        value?.let(RawCaptureSourceType::valueOf)

    @TypeConverter
    fun fromRawCaptureIngestionStatus(value: RawCaptureIngestionStatus?): String? = value?.name

    @TypeConverter
    fun toRawCaptureIngestionStatus(value: String?): RawCaptureIngestionStatus? =
        value?.let(RawCaptureIngestionStatus::valueOf)

    @TypeConverter
    fun fromParsedTransactionKind(value: ParsedTransactionKind?): String? = value?.name

    @TypeConverter
    fun toParsedTransactionKind(value: String?): ParsedTransactionKind? =
        value?.let(ParsedTransactionKind::valueOf)

    @TypeConverter
    fun fromTransactionCandidateType(value: TransactionCandidateType?): String? = value?.name

    @TypeConverter
    fun toTransactionCandidateType(value: String?): TransactionCandidateType? =
        value?.let(TransactionCandidateType::valueOf)

    @TypeConverter
    fun fromCandidateDecisionState(value: CandidateDecisionState?): String? = value?.name

    @TypeConverter
    fun toCandidateDecisionState(value: String?): CandidateDecisionState? =
        value?.let(CandidateDecisionState::valueOf)

    @TypeConverter
    fun fromCandidateDecisionReason(value: CandidateDecisionReason?): String? = value?.name

    @TypeConverter
    fun toCandidateDecisionReason(value: String?): CandidateDecisionReason? =
        value?.let(CandidateDecisionReason::valueOf)

    @TypeConverter
    fun fromSyncStatus(value: SyncStatus?): String? = value?.name

    @TypeConverter
    fun toSyncStatus(value: String?): SyncStatus? = value?.let(SyncStatus::valueOf)
}
