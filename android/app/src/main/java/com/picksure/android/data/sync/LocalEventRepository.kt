package com.picksure.android.data.sync

import android.content.Context
import com.picksure.android.data.local.PickEventEntity
import com.picksure.android.data.local.PickSureDatabase
import java.util.UUID

class LocalEventRepository(private val context: Context) {
    private val dao = PickSureDatabase.get(context).pickEventDao()

    suspend fun record(
        eventType: String,
        taskId: String,
        taskItemId: String? = null,
        sku: String? = null,
        barcode: String? = null,
        quantity: Int = 0,
        reason: String? = null,
        replacementSku: String? = null,
        approvalStatus: String? = null,
        lotId: String? = null,
        lotTag: String? = null,
    ): String {
        val id = UUID.randomUUID().toString()
        val createdAt = maxOf(System.currentTimeMillis(), (dao.latestCreatedAt() ?: 0L) + 1L)
        dao.insert(PickEventEntity(
            eventUuid = id, eventType = eventType, taskId = taskId, taskItemId = taskItemId,
            sku = sku, barcode = barcode, quantity = quantity, reason = reason,
            replacementSku = replacementSku, approvalStatus = approvalStatus, createdAt = createdAt,
            lotId = lotId, lotTag = lotTag,
        ))
        SyncScheduler.schedule(context)
        return id
    }

    suspend fun retry() {
        dao.retryAttentionEvents()
        SyncScheduler.schedule(context)
    }
}
