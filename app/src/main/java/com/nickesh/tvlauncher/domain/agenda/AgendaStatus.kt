package com.nickesh.tvlauncher.domain.agenda

enum class BlockStatus { NOT_CONNECTED, UNREACHABLE, EMPTY, OK }

enum class WatchingState { NEEDS_PERMISSION, DISMISSED, EMPTY, OK }

object AgendaStatus {
    /**
     * Up next / To do status. A failed refresh that kept old data stays [BlockStatus.OK] (or [BlockStatus.EMPTY]);
     * [BlockStatus.UNREACHABLE] only when there is nothing to show from any fetch.
     */
    fun block(connected: Boolean, hasCache: Boolean, ok: Boolean, cachedEmpty: Boolean, rowsEmpty: Boolean): BlockStatus =
        when {
            !connected -> BlockStatus.NOT_CONNECTED
            !hasCache || (!ok && cachedEmpty) -> BlockStatus.UNREACHABLE
            rowsEmpty -> BlockStatus.EMPTY
            else -> BlockStatus.OK
        }

    fun watching(permitted: Boolean, dismissed: Boolean, tilesEmpty: Boolean): WatchingState =
        when {
            !permitted && !dismissed -> WatchingState.NEEDS_PERMISSION
            !permitted -> WatchingState.DISMISSED
            tilesEmpty -> WatchingState.EMPTY
            else -> WatchingState.OK
        }
}
