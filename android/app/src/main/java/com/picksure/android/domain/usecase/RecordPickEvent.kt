package com.picksure.android.domain.usecase

import com.picksure.android.domain.repository.PickEventRepository

class RecordPickEvent(private val repository: PickEventRepository) {
    suspend operator fun invoke(
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
    ): String = repository.record(eventType, taskId, taskItemId, sku, barcode, quantity, reason,
        replacementSku, approvalStatus, lotId, lotTag)
}
