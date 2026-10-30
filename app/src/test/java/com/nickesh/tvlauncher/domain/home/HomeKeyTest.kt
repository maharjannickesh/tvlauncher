package com.nickesh.tvlauncher.domain.home

import com.nickesh.tvlauncher.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeKeyTest {
    @Test fun homeOnHome_isNoop() { assertNull(HomeKey.next(Screen.Home)) }
    @Test fun welcomeIgnoresHome() { assertNull(HomeKey.next(Screen.Welcome)) }
    @Test fun subScreensReturnHome() {
        assertEquals(Screen.Home, HomeKey.next(Screen.AllApps()))
        assertEquals(Screen.Home, HomeKey.next(Screen.Settings))
        assertEquals(Screen.Home, HomeKey.next(Screen.Background))
    }
}
