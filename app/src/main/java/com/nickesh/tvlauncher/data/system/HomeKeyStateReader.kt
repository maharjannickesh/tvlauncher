package com.nickesh.tvlauncher.data.system

import android.content.Context
import android.provider.Settings
import com.nickesh.tvlauncher.domain.home.HomeKeyState

/** Reads the live accessibility settings and asks the pure [HomeKeyState] whether our service is on. */
fun HomeKeyState.read(context: Context): Boolean {
    val resolver = context.contentResolver
    return isEnabled(
        Settings.Secure.getString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
        Settings.Secure.getInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0),
    )
}
