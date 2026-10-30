package com.nickesh.tvlauncher.data.weather

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class GeoMatchTest {
    @Test fun labelSkipsNulls() { assertEquals("Lisbon, Portugal", GeoMatch("Lisbon", null, "Portugal", 1.0, 2.0, "Europe/Lisbon").label) }
    @Test fun decodesOpenMeteoShape() { val j = """{"results":[{"name":"Lisbon","admin1":"Lisboa","country":"Portugal","latitude":38.7,"longitude":-9.1,"timezone":"Europe/Lisbon","id":1}]}"""; val r = Json { ignoreUnknownKeys = true }.decodeFromString<GeoResponse>(j); assertEquals("Europe/Lisbon", r.results!![0].timezone) }
}
