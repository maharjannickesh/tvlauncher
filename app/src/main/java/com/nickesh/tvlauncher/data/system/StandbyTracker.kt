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

    /** Returns whether the most recent off→on cycle qualifies for a replay, then clears it. */
    fun takeReplay(thresholdMs: Long = WelcomeReplay.THRESHOLD_MS): Boolean {
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
