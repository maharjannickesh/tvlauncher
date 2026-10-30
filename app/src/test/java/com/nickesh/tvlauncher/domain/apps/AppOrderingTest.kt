package com.nickesh.tvlauncher.domain.apps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppOrderingTest {
    private fun app(label: String, pkg: String = "pkg.${label.lowercase()}") = InstalledApp(pkg, label)

    @Test fun favouritesFirst_thenAlpha() {
        val installed = listOf(app("Alpha"), app("Beta"), app("Gamma"), app("Delta"))
        val result = AppOrdering.order(installed, listOf("pkg.gamma", "pkg.beta"))
        assertEquals(listOf("Gamma", "Beta", "Alpha", "Delta"), result.map { it.label })
    }

    @Test fun missingFavourites_skipped() {
        val installed = listOf(app("Alpha"), app("Beta"))
        val result = AppOrdering.order(installed, listOf("pkg.gone", "pkg.beta"))
        assertEquals(listOf("Beta", "Alpha"), result.map { it.label })
    }

    @Test fun max8_enforced() {
        val installed = (1..12).map { app("App%02d".format(it)) }
        assertEquals(8, AppOrdering.order(installed, emptyList()).size)
    }

    @Test fun empty_installed_returnsEmpty() {
        assertTrue(AppOrdering.order(emptyList(), listOf("pkg.a")).isEmpty())
    }

    @Test fun threeApps_returnsThree() {
        val installed = listOf(app("C"), app("A"), app("B"))
        assertEquals(listOf("A", "B", "C"), AppOrdering.order(installed, emptyList()).map { it.label })
    }

    @Test fun alpha_isCaseInsensitive() {
        val installed = listOf(app("Zattoo"), app("youtube"))
        assertEquals(listOf("youtube", "Zattoo"), AppOrdering.order(installed, emptyList()).map { it.label })
    }
}
