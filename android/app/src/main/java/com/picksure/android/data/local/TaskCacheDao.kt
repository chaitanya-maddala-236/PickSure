package com.picksure.android.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskCacheDao {
    @Transaction
    @Query("SELECT * FROM task_cache ORDER BY id ASC")
    fun observeSnapshot(): Flow<List<CachedTaskSnapshot>>

    @Query("DELETE FROM task_item_cache")
    suspend fun clearItems()

    @Query("DELETE FROM task_cache")
    suspend fun clearTasks()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putTasks(tasks: List<TaskCacheEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putItems(items: List<TaskItemCacheEntity>)
}
