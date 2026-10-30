package com.nickesh.tvlauncher.domain.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class IdleOptionsTest {
    @Test fun label() { assertEquals("10 min", IdleOptions.label(10)) }
    @Test fun indexExact() { assertEquals(3, IdleOptions.indexFor(10)) }
    @Test fun indexNearestLargerOrLast() { assertEquals(2, IdleOptions.indexFor(4)); assertEquals(6, IdleOptions.indexFor(90)); assertEquals(3, IdleOptions.indexFor(null)) }
    @Test fun indexEdges() { assertEquals(6, IdleOptions.indexFor(60)); assertEquals(0, IdleOptions.indexFor(0)) }
}
