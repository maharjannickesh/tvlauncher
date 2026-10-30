package com.nickesh.tvlauncher.data.system

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.speech.RecognizerIntent
import android.util.Log
import com.nickesh.tvlauncher.ui.home.SystemAction

/** Resolves the top-bar system actions to the first intent this TV can actually handle. */
class SystemIntents(private val context: Context) {

    fun candidates(action: SystemAction): List<Intent> = when (action) {
        SystemAction.ASSIST -> assist()
        SystemAction.SEARCH -> listOf(
            Intent(SearchManager.INTENT_ACTION_GLOBAL_SEARCH),
            Intent(Intent.ACTION_SEARCH),
        ) + assist()
        SystemAction.INPUTS -> listOf(Intent("com.android.tv.settings.INPUTS"))
        SystemAction.PROFILE -> listOf(
            Intent(Settings.ACTION_SYNC_SETTINGS),
            Intent(Settings.ACTION_SETTINGS),
        )
    }

    fun isAvailable(action: SystemAction): Boolean = candidates(action).any { resolves(it) }

    fun perform(action: SystemAction): Boolean {
        for (intent in candidates(action)) {
            if (!resolves(intent)) continue
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                Log.i(TAG, "$action -> ${intent.action}")
                return true
            } catch (_: ActivityNotFoundException) {
            } catch (_: SecurityException) {
            }
        }
        Log.i(TAG, "$action -> none")
        return false
    }

    /** True when the stock launcher package exists, including when it is disabled. */
    fun stockHomeInstalled(): Boolean =
        runCatching { context.packageManager.getApplicationInfo(STOCK_HOME_PACKAGE, 0) }.isSuccess

    /** Opens the system app-details page of the stock launcher (where Enable lives). */
    fun openStockHomeDetails(): Boolean = try {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$STOCK_HOME_PACKAGE"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    } catch (_: ActivityNotFoundException) {
        false
    }

    private fun resolves(intent: Intent): Boolean =
        context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null

    private fun assist() = listOf(
        Intent(Intent.ACTION_ASSIST),
        Intent(RecognizerIntent.ACTION_VOICE_SEARCH_HANDS_FREE),
        Intent(Intent.ACTION_VOICE_COMMAND),
    )

    companion object {
        const val STOCK_HOME_PACKAGE = "com.google.android.apps.tv.launcherx"
        private const val TAG = "SystemIntents"
    }
}
