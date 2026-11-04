package com.picksure.android.data.sync

import android.content.Context
import com.picksure.android.data.local.PickEventEntity
import com.picksure.android.data.local.PickSureDatabase
import com.picksure.android.domain.model.PickEvent
import com.picksure.android.domain.repository.PickEventRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class LocalEventRepository(private val context: Context) : PickEventRepository {
    private val dao = PickSureDatabase.get(context).pickEventDao()

    override fun observeEvents(): Flow<List<PickEvent>> = dao.observeAll().map { rows -> rows.map { row ->
        PickEvent(row.eventUuid, row.eventType, row.taskId, row.taskItemId, row.sku, row.barcode, row.quantity,
            row.reason, row.replacementSku, row.approvalStatus, row.createdAt, row.syncStatus, row.lastError,
            row.lotId, row.lotTag)
    } }

    override suspend fun record(
        eventType: String,
        taskId: String,
        taskItemId: String?,
        sku: String?,
        barcode: String?,
        quantity: Int,
        reason: String?,
        replacementSku: String?,
        approvalStatus: String?,
        lotId: String?,
        lotTag: String?,
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

    override suspend fun retry() {
        dao.retryAttentionEvents()
        SyncScheduler.schedule(context)
    }
}
