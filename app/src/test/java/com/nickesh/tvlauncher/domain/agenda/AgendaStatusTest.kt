package com.nickesh.tvlauncher.domain.agenda

import org.junit.Assert.assertEquals
import org.junit.Test

class AgendaStatusTest {
    private fun block(connected: Boolean = true, hasCache: Boolean = true, ok: Boolean = true, cachedEmpty: Boolean = false, rowsEmpty: Boolean = false) =
        AgendaStatus.block(connected, hasCache, ok, cachedEmpty, rowsEmpty)

    @Test fun notConnected_winsOverEverything() = assertEquals(BlockStatus.NOT_CONNECTED, block(connected = false, hasCache = false))

    @Test fun unreachable_whenNoCacheYet() = assertEquals(BlockStatus.UNREACHABLE, block(hasCache = false, rowsEmpty = true))

    @Test fun unreachable_whenFailedWithNothingCached() =
        assertEquals(BlockStatus.UNREACHABLE, block(ok = false, cachedEmpty = true, rowsEmpty = true))

    @Test fun ok_whenFailedButOldDataKept() = assertEquals(BlockStatus.OK, block(ok = false))

    @Test fun empty_whenLoadedWithNoRows() = assertEquals(BlockStatus.EMPTY, block(rowsEmpty = true))

    @Test fun empty_whenCacheHasOnlyPastItems() = assertEquals(BlockStatus.EMPTY, block(ok = false, rowsEmpty = true))

    @Test fun ok_whenRows() = assertEquals(BlockStatus.OK, block())

    @Test fun watching_needsPermission_untilDismissed() {
        assertEquals(WatchingState.NEEDS_PERMISSION, AgendaStatus.watching(permitted = false, dismissed = false, tilesEmpty = true))
        assertEquals(WatchingState.DISMISSED, AgendaStatus.watching(permitted = false, dismissed = true, tilesEmpty = true))
    }

    @Test fun watching_permitted_isEmptyOrOk_evenIfOnceDismissed() {
        assertEquals(WatchingState.EMPTY, AgendaStatus.watching(permitted = true, dismissed = true, tilesEmpty = true))
        assertEquals(WatchingState.OK, AgendaStatus.watching(permitted = true, dismissed = false, tilesEmpty = false))
    }
}
