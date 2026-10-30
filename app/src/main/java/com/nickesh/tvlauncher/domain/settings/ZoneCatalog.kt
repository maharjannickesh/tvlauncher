package com.nickesh.tvlauncher.domain.settings

import java.time.ZoneId

object ZoneCatalog {
    private val named = listOf("Africa", "America", "Antarctica", "Asia", "Atlantic", "Australia", "Europe", "Indian", "Pacific")
    const val OTHER = "Other"
    val regions: List<String> = named + OTHER

    private val byRegion: Map<String, List<String>> =
        ZoneId.getAvailableZoneIds().sorted().groupBy { regionOf(it) }

    fun zones(region: String): List<String> = byRegion[region].orEmpty()

    fun regionOf(zoneId: String): String =
        named.firstOrNull { zoneId.startsWith("$it/") } ?: OTHER
}
