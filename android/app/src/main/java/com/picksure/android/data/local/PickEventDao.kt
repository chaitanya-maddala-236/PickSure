package com.picksure.android.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PickEventDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(event: PickEventEntity)

    @Query("SELECT * FROM pick_events ORDER BY createdAt ASC, eventUuid ASC")
    fun observeAll(): Flow<List<PickEventEntity>>

    @Query("SELECT * FROM pick_events WHERE syncStatus = 'PENDING' ORDER BY createdAt ASC, eventUuid ASC LIMIT :limit")
    suspend fun pending(limit: Int = 50): List<PickEventEntity>

    @Query("SELECT MAX(createdAt) FROM pick_events")
    suspend fun latestCreatedAt(): Long?

    @Query("UPDATE pick_events SET syncStatus = 'SYNCING', lastError = NULL WHERE eventUuid IN (:eventUuids)")
    suspend fun markSyncing(eventUuids: List<String>)

    @Query("UPDATE pick_events SET syncStatus = 'PENDING' WHERE syncStatus = 'SYNCING'")
    suspend fun resetInterruptedSyncs()

    @Query("UPDATE pick_events SET syncStatus = :status, lastError = :error WHERE eventUuid = :eventUuid")
    suspend fun setStatus(eventUuid: String, status: String, error: String? = null)

    @Query("UPDATE pick_events SET syncStatus = 'PENDING', lastError = NULL WHERE syncStatus = 'NEEDS_ATTENTION'")
    suspend fun retryAttentionEvents()
}
