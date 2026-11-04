package com.picksure.android.domain.usecase

import com.picksure.android.domain.model.PickEvent

object PickedProgress {
    fun calculate(itemId: String, serverPicked: Int, events: List<PickEvent>): Int {
        val related = events.filter { it.taskItemId == itemId && it.eventType == "PICK" }
        val acknowledged = related.filter { it.syncStatus == "SYNCED" }.sumOf { it.quantity }
        val waiting = related.filter { it.syncStatus == "PENDING" || it.syncStatus == "SYNCING" }.sumOf { it.quantity }
        return maxOf(serverPicked, acknowledged) + waiting
    }
}
