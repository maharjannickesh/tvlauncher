package com.nickesh.tvlauncher.domain.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeKeyStateTest {
    private val short = "com.nickesh.tvlauncher/.HomeKeyService"
    private val full = "com.nickesh.tvlauncher/com.nickesh.tvlauncher.HomeKeyService"
    @Test fun shortForm_enabled() { assertTrue(HomeKeyState.isEnabled(short, 1)) }
    @Test fun fullForm_amongOthers_withWhitespace() {
        assertTrue(HomeKeyState.isEnabled("com.google.android.marvin.talkback/.TalkBackService: $full :", 1))
    }
    @Test fun listed_butAccessibilityOff() { assertFalse(HomeKeyState.isEnabled(short, 0)) }
    @Test fun nullOrEmpty_isOff() { assertFalse(HomeKeyState.isEnabled(null, 1)); assertFalse(HomeKeyState.isEnabled("", 1)) }
    @Test fun otherServiceOnly_isOff() { assertFalse(HomeKeyState.isEnabled("com.other/.HomeKeyService", 1)) }
}
