package com.picksure.android.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import retrofit2.HttpException
import com.picksure.android.data.local.PickSureDatabase
import com.picksure.android.data.remote.ApiFactory
import com.picksure.android.data.remote.SyncBatchRequest
import com.picksure.android.data.remote.SyncEventRequest
import java.time.Instant

class PickSureSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("picksure", Context.MODE_PRIVATE)
        val dao = PickSureDatabase.get(applicationContext).pickEventDao()
        dao.resetInterruptedSyncs()
        if (prefs.getString("token", null).isNullOrBlank()) return Result.success()
        val batch = dao.pending()
        if (batch.isEmpty()) return Result.success()
        dao.markSyncing(batch.map { it.eventUuid })
        return try {
            val requests = batch.map { row ->
                SyncEventRequest(
                    event_uuid = row.eventUuid,
                    event_type = row.eventType,
                    task_id = row.taskId,
                    task_item_id = row.taskItemId,
                    sku = row.sku,
                    barcode = row.barcode,
                    lot_id = row.lotId,
                    lot_tag = row.lotTag,
                    quantity = row.quantity,
                    reason = row.reason,
                    replacement_sku = row.replacementSku,
                    approval_status = row.approvalStatus,
                    created_at = Instant.ofEpochMilli(row.createdAt).toString(),
                )
            }
            val response = ApiFactory.create(applicationContext).syncEvents(SyncBatchRequest(requests))
            val byId = response.results.associateBy { it.event_uuid }
            batch.forEach { event ->
                val result = byId[event.eventUuid]
                if (result == null) {
                    dao.setStatus(event.eventUuid, "PENDING")
                    return@forEach
                }
                when (result.status) {
                    "accepted", "duplicate" -> dao.setStatus(event.eventUuid, "SYNCED")
                    "rejected" -> dao.setStatus(event.eventUuid, "NEEDS_ATTENTION", result.message)
                }
            }
            if (response.results.size < batch.size) Result.retry()
            else if (dao.pending(1).isNotEmpty()) {
                SyncScheduler.schedule(applicationContext)
                Result.success()
            } else Result.success()
        } catch (error: HttpException) {
            if (error.code() == 401 || error.code() == 403 || (error.code() in 400..499 && error.code() !in setOf(408, 429))) {
                val reason = when (error.code()) {
                    401 -> "Sign in again to sync this event."
                    403 -> "The server denied access to this task. Review the employee assignment."
                    else -> "The server rejected the sync request (${error.code()}). Review the local events."
                }
                batch.forEach { dao.setStatus(it.eventUuid, "NEEDS_ATTENTION", reason) }
                Result.success()
            } else {
                batch.forEach { dao.setStatus(it.eventUuid, "PENDING") }
                Result.retry()
            }
        } catch (_: Exception) {
            batch.forEach { dao.setStatus(it.eventUuid, "PENDING") }
            Result.retry()
        }
    }
}
