package com.nickesh.tvlauncher.data.system

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StandbyTrackerTest {
    private var now = 0L
    private val tracker = StandbyTracker { now }
    private val min = 60_000L

    @Test fun longStandby_replaysOnce() {
        tracker.onScreenOff(); now += 11 * min; tracker.onScreenOn()
        assertTrue(tracker.takeReplay()); assertFalse(tracker.takeReplay())
    }

    @Test fun shortStandby_noReplay_andClears() {
        tracker.onScreenOff(); now += 30_000; tracker.onScreenOn()
        assertFalse(tracker.takeReplay()); now += 60 * min; assertFalse(tracker.takeReplay())
    }

    @Test fun coldProcess_noReplay() { assertFalse(tracker.takeReplay()) }

    @Test fun dreamTimeDoesNotCount() {
        // Dream ran 30 min with the screen on; only the off interval (12 h) counts and it qualifies.
        now += 30 * min; tracker.onScreenOff(); now += 12 * 60 * min; tracker.onScreenOn()
        assertTrue(tracker.takeReplay())
    }

    @Test fun offAgainWithoutHomeReArms() {
        tracker.onScreenOff(); now += 5 * min; tracker.onScreenOn()
        now += 1 * min; tracker.onScreenOff(); now += 5 * min; tracker.onScreenOn()
        assertFalse(tracker.takeReplay()) // second cycle measured from its own screen-off, not the first
    }

    @Test fun stillAsleep_noReplay() { tracker.onScreenOff(); now += 20 * min; assertFalse(tracker.takeReplay()) }

    @Test fun startBeforeScreenOnBroadcast_stillReplays() {
        tracker.onScreenOff(); now += 11 * min; tracker.onInteractiveStart()
        assertTrue(tracker.takeReplay()); now += 1_000; tracker.onScreenOn(); assertFalse(tracker.takeReplay())
    }

    @Test fun interactiveStartWithoutOff_noReplay() { tracker.onInteractiveStart(); assertFalse(tracker.takeReplay()) }
}
