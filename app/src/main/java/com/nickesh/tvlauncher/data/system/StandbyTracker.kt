package com.nickesh.tvlauncher.data.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.nickesh.tvlauncher.domain.home.WelcomeReplay

/**
 * Records screen-off and screen-on times so the home screen can tell whether this wake
 * followed a long standby. Answers once per off→on cycle.
 */
class StandbyTracker(private val clock: () -> Long = SystemClock::elapsedRealtime) {

    private var screenOffAt: Long? = null
    private var screenOnAt: Long? = null

    fun onScreenOff() {
        screenOffAt = clock()
        screenOnAt = null
    }

    fun onScreenOn() {
        screenOnAt = clock()
    }

    /**
     * Called when the UI starts while the display is interactive. On the Chromecast the activity's
     * ON_START arrives ~1.3 s before ACTION_SCREEN_ON, so an off with no matching on yet is treated
     * as ending now.
     */
    fun onInteractiveStart() {
        if (screenOffAt != null && screenOnAt == null) screenOnAt = clock()
    }

    /** Returns whether the most recent off→on cycle qualifies for a replay, then clears it. */
    fun takeReplay(thresholdMs: Long = WelcomeReplay.THRESHOLD_MS): Boolean {
        // Still asleep (off recorded, no on yet): keep the pending off for the real wake.
        if (screenOffAt != null && screenOnAt == null) return false
        val replay = WelcomeReplay.shouldReplay(screenOffAt, screenOnAt, thresholdMs)
        screenOffAt = null
        screenOnAt = null
        return replay
    }

    fun register(context: Context) {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        ContextCompat.registerReceiver(
            context,
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    when (intent.action) {
                        Intent.ACTION_SCREEN_OFF -> {
                            onScreenOff()
                            Log.i(TAG, "screen off")
                        }
                        Intent.ACTION_SCREEN_ON -> {
                            onScreenOn()
                            Log.i(TAG, "screen on")
                        }
                    }
                }
            },
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    private companion object {
        const val TAG = "Standby"
    }
}
