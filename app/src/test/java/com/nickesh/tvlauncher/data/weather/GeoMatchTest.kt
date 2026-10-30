package com.nickesh.tvlauncher.data.weather

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class GeoMatchTest {
    @Test fun labelSkipsNulls() { assertEquals("Lisbon, Portugal", GeoMatch("Lisbon", null, "Portugal", 1.0, 2.0, "Europe/Lisbon").label) }
    @Test fun validDropsNullAndUnknownZones() {
        val j = """{"results":[
            {"name":"Lisbon","country":"Portugal","latitude":38.7,"longitude":-9.1,"timezone":"Europe/Lisbon"},
            {"name":"Nowhere","latitude":1.0,"longitude":2.0},
            {"name":"Mars","latitude":3.0,"longitude":4.0,"timezone":"Mars/Olympus_Mons"}
        ]}"""
        val hits = Json { ignoreUnknownKeys = true }.decodeFromString<GeoResponse>(j).results!!
        val kept = GeoMatches.valid(hits, java.time.ZoneId.getAvailableZoneIds())
        assertEquals(listOf(GeoMatch("Lisbon", null, "Portugal", 38.7, -9.1, "Europe/Lisbon")), kept)
    }
    @Test fun decodesOpenMeteoShape() { val j = """{"results":[{"name":"Lisbon","admin1":"Lisboa","country":"Portugal","latitude":38.7,"longitude":-9.1,"timezone":"Europe/Lisbon","id":1}]}"""; val r = Json { ignoreUnknownKeys = true }.decodeFromString<GeoResponse>(j); assertEquals("Europe/Lisbon", r.results!![0].timezone) }
}
