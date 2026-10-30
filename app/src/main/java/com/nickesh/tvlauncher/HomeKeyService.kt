package com.nickesh.tvlauncher

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.nickesh.tvlauncher.domain.home.HomeKeyDecision
import com.nickesh.tvlauncher.domain.home.Press

/**
 * Owns the remote's Home key: short press opens [MainActivity], long press opens the
 * system quick-settings dashboard. The remote sends one DOWN and one UP, so the decision
 * is the hold duration at UP.
 */
class HomeKeyService : AccessibilityService() {

    private var decision = HomeKeyDecision()
    private var pendingDownMs: Long? = null

    override fun onServiceConnected() {
        serviceInfo?.let { info ->
            serviceInfo = info.apply { flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS }
        }
        val uptime = SystemClock.elapsedRealtime()
        Log.i(TAG, "connected uptime=$uptime")
        if (uptime < BOOT_WINDOW_MS) openHome("boot")
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_HOME) return false
        return when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                val pending = pendingDownMs
                if (pending != null && event.repeatCount == 0 && event.eventTime - pending > STALE_DOWN_MS) {
                    Log.i(TAG, "stale pending DOWN (${event.eventTime - pending} ms) → fresh decision")
                    decision = HomeKeyDecision()
                    pendingDownMs = null
                }
                if (pendingDownMs == null) pendingDownMs = event.eventTime
                decision.onDown(event.eventTime)
            }
            KeyEvent.ACTION_UP -> {
                pendingDownMs = null
                when (decision.onUp(event.eventTime)) {
                    Press.SHORT -> openHome("short")
                    Press.LONG -> {
                        Log.i(TAG, "long → dashboard ${performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)}")
                        true
                    }
                    Press.NONE -> false
                }
            }
            else -> false
        }
    }

    private fun openHome(why: String): Boolean = try {
        startActivity(
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        Log.i(TAG, "$why → home")
        true
    } catch (e: Exception) {
        Log.w(TAG, "$why → home failed", e)
        false
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    companion object {
        const val BOOT_WINDOW_MS = 90_000L
        const val TAG = "HomeKey"
        private const val STALE_DOWN_MS = 5_000L
    }
}
