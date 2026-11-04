package com.picksure.android.domain.repository

import com.picksure.android.domain.model.PickEvent
import kotlinx.coroutines.flow.Flow

interface PickEventRepository {
    fun observeEvents(): Flow<List<PickEvent>>
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
    ): String
    suspend fun retry()
}
