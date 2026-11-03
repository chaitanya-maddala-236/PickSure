package com.picksure.android.data.local

import androidx.room.Embedded
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "task_cache")
data class TaskCacheEntity(
    @PrimaryKey val id: String,
    val orderId: String,
    val priority: String,
    val status: String,
)

@Entity(tableName = "task_item_cache", indices = [Index(value = ["taskId"])])
data class TaskItemCacheEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    val orderId: String,
    val sku: String,
    val productName: String,
    val barcode: String,
    val requestedQty: Int,
    val aisle: String,
    val rack: String,
    val shelf: String,
    val serverPicked: Int,
    val serverStatus: String,
    val recommendedLotId: String? = null,
    val lotCode: String? = null,
    val lotTag: String? = null,
    val expiresOn: String? = null,
    @ColumnInfo(defaultValue = "'no_stock'") val lotStatus: String = "no_stock",
)

data class CachedTaskSnapshot(
    @Embedded val task: TaskCacheEntity,
    @Relation(parentColumn = "id", entityColumn = "taskId")
    val items: List<TaskItemCacheEntity>,
)
