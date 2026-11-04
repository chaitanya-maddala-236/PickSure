package com.picksure.shared

data class RouteStop(val sku: String, val aisle: String, val rack: String, val shelf: String)

object ShelfRoutePlanner {
    fun order(stops: List<RouteStop>): List<RouteStop> =
        stops.sortedWith(compareBy<RouteStop>({ it.aisle }, { it.rack }, { it.shelf }, { it.sku }))
}
