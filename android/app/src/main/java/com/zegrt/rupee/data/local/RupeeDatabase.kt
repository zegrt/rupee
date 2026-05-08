package com.zegrt.rupee.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.zegrt.rupee.data.local.dao.AccountDao
import com.zegrt.rupee.data.local.dao.BucketDao
import com.zegrt.rupee.data.local.dao.CanonicalTransactionDao
import com.zegrt.rupee.data.local.dao.CategoryDao
import com.zegrt.rupee.data.local.dao.CreditCardDao
import com.zegrt.rupee.data.local.dao.BudgetDao
import com.zegrt.rupee.data.local.dao.EmiPlanDao
import com.zegrt.rupee.data.local.dao.InboxItemDao
import com.zegrt.rupee.data.local.dao.MerchantTrustRuleDao
import com.zegrt.rupee.data.local.dao.ParsedSignalDao
import com.zegrt.rupee.data.local.dao.RawCaptureEventDao
import com.zegrt.rupee.data.local.dao.RecurringPatternDao
import com.zegrt.rupee.data.local.dao.TransactionCandidateDao
import com.zegrt.rupee.data.local.dao.UserDao
import com.zegrt.rupee.data.local.entity.AccountEntity
import com.zegrt.rupee.data.local.entity.BucketEntity
import com.zegrt.rupee.data.local.entity.BudgetEntity
import com.zegrt.rupee.data.local.entity.CanonicalTransactionEntity
import com.zegrt.rupee.data.local.entity.CategoryEntity
import com.zegrt.rupee.data.local.entity.CreditCardEntity
import com.zegrt.rupee.data.local.entity.EmiPlanEntity
import com.zegrt.rupee.data.local.entity.InboxItemEntity
import com.zegrt.rupee.data.local.entity.MerchantTrustRuleEntity
import com.zegrt.rupee.data.local.entity.ParsedSignalEntity
import com.zegrt.rupee.data.local.entity.RawCaptureEventEntity
import com.zegrt.rupee.data.local.entity.RecurringPatternEntity
import com.zegrt.rupee.data.local.entity.TransactionCandidateEntity
import com.zegrt.rupee.data.local.entity.TransactionBucketAssignmentEntity
import com.zegrt.rupee.data.local.entity.UserEntity

@Database(
    entities = [
        UserEntity::class,
        AccountEntity::class,
        CreditCardEntity::class,
        CategoryEntity::class,
        BucketEntity::class,
        CanonicalTransactionEntity::class,
        TransactionBucketAssignmentEntity::class,
        BudgetEntity::class,
        InboxItemEntity::class,
        EmiPlanEntity::class,
        RawCaptureEventEntity::class,
        ParsedSignalEntity::class,
        TransactionCandidateEntity::class,
        MerchantTrustRuleEntity::class,
        RecurringPatternEntity::class,
    ],
    version = 7,
    exportSchema = true,
)
@TypeConverters(RupeeTypeConverters::class)
abstract class RupeeDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun accountDao(): AccountDao
    abstract fun creditCardDao(): CreditCardDao
    abstract fun categoryDao(): CategoryDao
    abstract fun bucketDao(): BucketDao
    abstract fun budgetDao(): BudgetDao
    abstract fun canonicalTransactionDao(): CanonicalTransactionDao
    abstract fun inboxItemDao(): InboxItemDao
    abstract fun rawCaptureEventDao(): RawCaptureEventDao
    abstract fun parsedSignalDao(): ParsedSignalDao
    abstract fun transactionCandidateDao(): TransactionCandidateDao
    abstract fun merchantTrustRuleDao(): MerchantTrustRuleDao
    abstract fun emiPlanDao(): EmiPlanDao
    abstract fun recurringPatternDao(): RecurringPatternDao
}
