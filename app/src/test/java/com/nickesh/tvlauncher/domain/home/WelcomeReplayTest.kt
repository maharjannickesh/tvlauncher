package com.nickesh.tvlauncher.domain.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WelcomeReplayTest {
    private val min = 60_000L

    @Test fun gapAtOrOverThreshold_replays() {
        assertTrue(WelcomeReplay.shouldReplay(1_000L, 1_000L + 10 * min))
        assertTrue(WelcomeReplay.shouldReplay(1_000L, 1_000L + 11 * min))
    }

    @Test fun shortGap_doesNotReplay() {
        assertFalse(WelcomeReplay.shouldReplay(1_000L, 1_000L + 9 * min + 59_000))
    }

    @Test fun missingTimestamps_doNotReplay() {
        assertFalse(WelcomeReplay.shouldReplay(null, 5L))
        assertFalse(WelcomeReplay.shouldReplay(5L, null))
        assertFalse(WelcomeReplay.shouldReplay(null, null))
    }

    @Test fun onBeforeOff_doesNotReplay() {
        assertFalse(WelcomeReplay.shouldReplay(20 * min, 5 * min))
    }

    @Test fun customThreshold() {
        assertTrue(WelcomeReplay.shouldReplay(0L, 30_000L, thresholdMs = 30_000L))
    }
}
