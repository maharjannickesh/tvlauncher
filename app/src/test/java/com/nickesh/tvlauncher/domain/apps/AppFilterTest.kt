package com.nickesh.tvlauncher.domain.apps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppFilterTest {
    private val apps = listOf("YouTube", "YouTube TV", "Netflix")
    @Test fun blank_all() { assertEquals(apps, AppFilter.filter(apps, "  ") { it }) }
    @Test fun caseInsensitiveContains() { assertEquals(listOf("YouTube", "YouTube TV"), AppFilter.filter(apps, "you") { it }) }
    @Test fun noMatch_isEmpty() { assertTrue(AppFilter.filter(apps, "zzz") { it }.isEmpty()) }
    @Test fun trims() { assertEquals(listOf("Netflix"), AppFilter.filter(apps, " net ") { it }) }
}
