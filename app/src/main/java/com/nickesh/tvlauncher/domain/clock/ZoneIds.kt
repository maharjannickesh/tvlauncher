package com.nickesh.tvlauncher.domain.clock

import android.util.Log
import java.time.ZoneId
import java.time.ZoneOffset

/** Persisted zone ids are read through here: one this device's tzdata lacks must degrade to UTC, never crash. */
object ZoneIds {
    fun safe(id: String): ZoneId = runCatching { ZoneId.of(id) }.getOrElse {
        runCatching { Log.w("ZoneIds", "unknown zone '$id' -> UTC: ${it.message}") }
        ZoneOffset.UTC
    }
}
