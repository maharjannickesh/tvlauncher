package com.nickesh.tvlauncher.data.watchnext

import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.media.tv.TvContract
import android.media.tv.TvContract.WatchNextPrograms as Col
import android.os.Handler
import android.os.Looper

class WatchNextReader(private val context: Context) {
    private val uri = TvContract.WatchNextPrograms.CONTENT_URI

    fun hasPermission(): Boolean =
        context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED

    fun read(): List<WatchNextRow> {
        if (!hasPermission()) return emptyList()
        return try {
            val projection = arrayOf(
                Col._ID, Col.COLUMN_PACKAGE_NAME, Col.COLUMN_TITLE,
                Col.COLUMN_SEASON_DISPLAY_NUMBER, Col.COLUMN_EPISODE_DISPLAY_NUMBER,
                Col.COLUMN_WATCH_NEXT_TYPE, Col.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS,
                Col.COLUMN_LAST_PLAYBACK_POSITION_MILLIS, Col.COLUMN_DURATION_MILLIS,
                Col.COLUMN_POSTER_ART_URI, Col.COLUMN_INTENT_URI, Col.COLUMN_BROWSABLE,
            )
            val out = ArrayList<WatchNextRow>()
            context.contentResolver.query(uri, projection, null, null, null)?.use { cur ->
                while (cur.moveToNext()) {
                    out += WatchNextRow(
                        id = cur.getLong(0),
                        packageName = cur.getString(1).orEmpty(),
                        title = cur.getString(2).orEmpty(),
                        season = cur.getString(3),
                        episode = cur.getString(4),
                        type = if (cur.isNull(5)) -1 else cur.getInt(5),
                        lastEngagedMs = if (cur.isNull(6)) 0L else cur.getLong(6),
                        positionMs = if (cur.isNull(7)) null else cur.getLong(7),
                        durationMs = if (cur.isNull(8)) null else cur.getLong(8),
                        posterUri = cur.getString(9),
                        intentUri = cur.getString(10),
                        browsable = !cur.isNull(11) && cur.getInt(11) == 1,
                    )
                }
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun observe(onChange: () -> Unit): () -> Unit {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = onChange()
        }
        context.contentResolver.registerContentObserver(uri, true, observer)
        return { context.contentResolver.unregisterContentObserver(observer) }
    }

    private companion object {
        const val PERMISSION = "android.permission.READ_TV_LISTINGS"
    }
}
