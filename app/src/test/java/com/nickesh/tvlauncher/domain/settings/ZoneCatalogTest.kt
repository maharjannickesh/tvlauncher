package com.nickesh.tvlauncher.domain.settings

import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoneCatalogTest {
    @Test fun everyZoneAppearsOnce() { val all = ZoneCatalog.regions.flatMap { ZoneCatalog.zones(it) }; assertEquals(ZoneId.getAvailableZoneIds().size, all.size); assertEquals(all.size, all.toSet().size) }
    @Test fun regionOf() { assertEquals("Asia", ZoneCatalog.regionOf("Asia/Kathmandu")); assertEquals("Other", ZoneCatalog.regionOf("UTC")); assertEquals("Other", ZoneCatalog.regionOf("Etc/GMT+3")) }
    @Test fun sorted() { val z = ZoneCatalog.zones("Europe"); assertEquals(z.sorted(), z); assertTrue("Europe/Lisbon" in z) }
}
