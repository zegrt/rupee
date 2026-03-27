package com.zegrt.rupee.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.zegrt.rupee.data.local.entity.BucketEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BucketDao {
    @Query("SELECT * FROM buckets ORDER BY sortOrder, name")
    fun observeBuckets(): Flow<List<BucketEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBuckets(buckets: List<BucketEntity>)
}

