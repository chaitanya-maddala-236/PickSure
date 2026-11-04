package com.picksure.android.data.remote

data class LoginRequest(val email: String, val password: String)
data class EmployeeDto(val id: String, val name: String, val role: String, val store_id: String)
data class LoginResponse(val access_token: String, val token_type: String, val employee: EmployeeDto)
data class TaskDto(val id: String, val order_id: String, val status: String, val priority: String, val item_count: Int, val picked_count: Int)
data class TaskItemDto(
    val id: String, val task_id: String, val sku: String, val product_name: String, val barcode: String,
    val requested_qty: Int, val picked_qty: Int, val status: String, val aisle: String, val rack: String, val shelf: String,
    val recommended_lot_id: String? = null, val lot_code: String? = null, val lot_tag: String? = null,
    val expires_on: String? = null, val lot_status: String = "no_stock",
)
data class SyncEventRequest(
    val event_uuid: String,
    val event_type: String,
    val task_id: String,
    val task_item_id: String? = null,
    val sku: String? = null,
    val barcode: String? = null,
    val lot_id: String? = null,
    val lot_tag: String? = null,
    val quantity: Int = 0,
    val reason: String? = null,
    val replacement_sku: String? = null,
    val approval_status: String? = null,
    val created_at: String? = null,
)
data class SyncBatchRequest(val events: List<SyncEventRequest>)
data class SyncResultDto(val event_uuid: String, val status: String, val message: String, val server_event_id: String?)
data class SyncBatchResponse(val results: List<SyncResultDto>)
data class ScanVerifyRequest(val task_item_id: String, val barcode: String)
data class ScanVerifyResponse(val valid: Boolean, val expected_sku: String, val scanned_sku: String?, val product_name: String?, val message: String)
data class LotVerifyRequest(val task_item_id: String, val lot_id: String, val lot_tag: String)
data class LotVerifyResponse(val valid: Boolean, val lot_id: String, val lot_code: String?, val expires_on: String?, val available_quantity: Int, val message: String)
data class FreshnessLotDto(
    val sku: String, val product_name: String, val lot_code: String, val lot_tag: String, val expires_on: String,
    val quantity: Int, val status: String, val aisle: String, val rack: String, val shelf: String,
)
data class FreshnessSummaryDto(
    val minimum_shelf_life_days: Int, val safe_units: Int, val near_expiry_units: Int,
    val expired_units: Int, val lots: List<FreshnessLotDto>,
)
data class FreshnessAdviceActionDto(val lot_code: String, val recommendation: String, val reason: String)
data class FreshnessAdviceDto(
    val provider: String,
    val model: String? = null,
    val summary: String,
    val actions: List<FreshnessAdviceActionDto> = emptyList(),
)
data class ApprovalRequest(val decision: String)
data class SubstitutionDto(val id: String, val task_item_id: String, val original_sku: String, val replacement_sku: String, val reason: String?, val approval_status: String)
