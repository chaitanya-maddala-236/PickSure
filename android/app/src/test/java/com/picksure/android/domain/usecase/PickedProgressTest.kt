package com.picksure.android.domain.usecase

import com.picksure.android.domain.model.PickEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class PickedProgressTest {
    @Test
    fun addsPendingAndSyncingPicksToLatestAcknowledgedServerCount() {
        val events = listOf(
            event("synced-1", "SYNCED", 2),
            event("synced-2", "SYNCED", 2),
            event("pending", "PENDING", 2),
            event("syncing", "SYNCING", 1),
            event("rejected", "NEEDS_ATTENTION", 8),
            event("other-item", "PENDING", 10, itemId = "item-2"),
        )
        assertEquals(8, PickedProgress.calculate("item-1", serverPicked = 5, events = events))
    }

    @Test
    fun doesNotDoubleCountSyncedEventsAlreadyIncludedByServer() {
        assertEquals(7, PickedProgress.calculate("item-1", serverPicked = 7, events = listOf(event("one", "SYNCED", 3))))
    }

    private fun event(id: String, status: String, quantity: Int, itemId: String = "item-1") = PickEvent(
        eventUuid = id, eventType = "PICK", taskId = "task-1", taskItemId = itemId,
        sku = "SKU-1", barcode = null, quantity = quantity, reason = null, replacementSku = null,
        approvalStatus = null, createdAt = 1L, syncStatus = status, lastError = null, lotId = null, lotTag = null,
    )
}
