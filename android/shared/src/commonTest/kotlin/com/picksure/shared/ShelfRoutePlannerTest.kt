package com.picksure.shared

import kotlin.test.Test
import kotlin.test.assertEquals

class ShelfRoutePlannerTest {
    @Test
    fun routeUsesAisleRackShelfThenSku() {
        val route = ShelfRoutePlanner.order(listOf(
            RouteStop("MILK", "A1", "R1", "S2"),
            RouteStop("BREAD", "A1", "R2", "S1"),
            RouteStop("YOGURT", "A1", "R1", "S3"),
            RouteStop("EGGS", "A2", "R1", "S1"),
        ))
        assertEquals(listOf("MILK", "YOGURT", "BREAD", "EGGS"), route.map { it.sku })
    }
}
