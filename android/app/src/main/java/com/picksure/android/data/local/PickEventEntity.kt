package com.picksure.android.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "pick_events", indices = [Index(value = ["eventUuid"], unique = true), Index(value = ["createdAt"])])
data class PickEventEntity(
    @PrimaryKey val eventUuid: String,
    val eventType: String,
    val taskId: String,
    val taskItemId: String?,
    val sku: String?,
    val barcode: String?,
    val quantity: Int,
    val reason: String?,
    val replacementSku: String?,
    val approvalStatus: String?,
    val createdAt: Long,
    val syncStatus: String = "PENDING",
    val lastError: String? = null,
    val lotId: String? = null,
    val lotTag: String? = null,
)
