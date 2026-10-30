package com.nickesh.tvlauncher.data.system

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.net.toUri
import android.provider.Settings
import android.util.Log

/** The system idle timeout (`SCREEN_OFF_TIMEOUT`) that starts the screensaver, in whole minutes. */
object IdleTimer {
    private const val TAG = "IdleTimer"

    /** Current timeout rounded up to whole minutes, or null when unreadable. */
    fun readIdleMinutes(context: Context): Int? = try {
        val ms = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT)
        if (ms <= 0) null else ((ms.toLong() + 59_999L) / 60_000L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    } catch (_: Settings.SettingNotFoundException) {
        null
    }

    fun canWriteIdle(context: Context): Boolean = Settings.System.canWrite(context)

    /** Writes the timeout; false when the permission is missing or the write fails. */
    fun writeIdleMinutes(context: Context, minutes: Int): Boolean {
        if (!canWriteIdle(context)) return false
        return try {
            val ok = Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, minutes * 60_000)
            Log.i(TAG, "screen_off_timeout -> ${minutes * 60_000} ($ok)")
            ok
        } catch (e: SecurityException) {
            Log.i(TAG, "screen_off_timeout write refused: ${e.message}")
            false
        }
    }

    /** Opens the system "Modify system settings" page for this app; false when the TV has none. */
    fun openWriteSettingsPage(context: Context): Boolean {
        val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, "package:${context.packageName}".toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) == null) {
            Log.i(TAG, "write settings page -> none")
            return false
        }
        return try {
            context.startActivity(intent)
            Log.i(TAG, "write settings page -> opened")
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }
}
