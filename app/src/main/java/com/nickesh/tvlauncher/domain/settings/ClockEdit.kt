package com.nickesh.tvlauncher.domain.settings

import com.nickesh.tvlauncher.data.settings.Settings
import com.nickesh.tvlauncher.data.settings.WorldClock
import com.nickesh.tvlauncher.data.weather.GeoMatch

/** The only ways to change a clock's place: a chosen search match or a chosen zone; typed text never applies. */
object ClockEdit {
    fun applyMatch(clock: WorldClock, m: GeoMatch): WorldClock =
        clock.copy(city = m.name, zoneId = m.timezone, lat = m.latitude, lon = m.longitude)

    fun applyHomeMatch(s: Settings, m: GeoMatch): Settings =
        s.copy(homeCity = m.name, homeZone = m.timezone, homeLat = m.latitude, homeLon = m.longitude)

    fun withZone(clock: WorldClock, zoneId: String): WorldClock = clock.copy(zoneId = zoneId)
}
