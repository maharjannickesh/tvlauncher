package com.nickesh.tvlauncher.domain.agenda

enum class BlockStatus { LOADING, NOT_CONNECTED, UNREACHABLE, EMPTY, OK }

enum class WatchingState { LOADING, NEEDS_PERMISSION, DISMISSED, EMPTY, OK }

object AgendaStatus {
    /**
     * Up next / To do status. Nothing is decided from default settings ([BlockStatus.LOADING] until they load). Usable
     * cached data shows at once; a failed refresh that kept old data stays [BlockStatus.OK] (or [BlockStatus.EMPTY]);
     * [BlockStatus.UNREACHABLE] only once a refresh attempt finished with nothing usable to show.
     */
    fun block(
        settingsLoaded: Boolean,
        connected: Boolean,
        hasCache: Boolean,
        ok: Boolean,
        cachedEmpty: Boolean,
        attempted: Boolean,
        rowsEmpty: Boolean,
    ): BlockStatus {
        val usable = hasCache && (ok || !cachedEmpty)
        return when {
            !settingsLoaded -> BlockStatus.LOADING
            !connected -> BlockStatus.NOT_CONNECTED
            !usable && !attempted -> BlockStatus.LOADING
            !usable -> BlockStatus.UNREACHABLE
            rowsEmpty -> BlockStatus.EMPTY
            else -> BlockStatus.OK
        }
    }

    /** [read] is whether a Watch Next read has completed since the permission was seen. */
    fun watching(settingsLoaded: Boolean, permitted: Boolean, dismissed: Boolean, read: Boolean, tilesEmpty: Boolean): WatchingState =
        when {
            !settingsLoaded -> WatchingState.LOADING
            !permitted && !dismissed -> WatchingState.NEEDS_PERMISSION
            !permitted -> WatchingState.DISMISSED
            !read -> WatchingState.LOADING
            tilesEmpty -> WatchingState.EMPTY
            else -> WatchingState.OK
        }
}
