package com.picksure.android.domain.model

data class PickEvent(
    val eventUuid: String,
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
    val syncStatus: String,
    val lastError: String?,
    val lotId: String?,
    val lotTag: String?,
)
