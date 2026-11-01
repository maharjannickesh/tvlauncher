package com.nickesh.tvlauncher.domain.agenda

import org.junit.Assert.assertEquals
import org.junit.Test

class AgendaStatusTest {
    private fun block(
        settingsLoaded: Boolean = true,
        connected: Boolean = true,
        hasCache: Boolean = true,
        ok: Boolean = true,
        cachedEmpty: Boolean = false,
        attempted: Boolean = true,
        rowsEmpty: Boolean = false,
    ) = AgendaStatus.block(settingsLoaded, connected, hasCache, ok, cachedEmpty, attempted, rowsEmpty)

    @Test fun loading_beforeSettingsLoad_evenIfDefaultsLookDisconnected() =
        assertEquals(BlockStatus.LOADING, block(settingsLoaded = false, connected = false, hasCache = false, attempted = false))

    @Test fun notConnected_fromLoadedSettings() = assertEquals(BlockStatus.NOT_CONNECTED, block(connected = false, hasCache = false))

    @Test fun loading_whileConnected_noCache_noAttemptYet() =
        assertEquals(BlockStatus.LOADING, block(hasCache = false, attempted = false, rowsEmpty = true))

    @Test fun loading_whenOnlyAFailedEmptyCache_andNoAttemptYet() =
        assertEquals(BlockStatus.LOADING, block(ok = false, cachedEmpty = true, attempted = false, rowsEmpty = true))

    @Test fun unreachable_onlyAfterFailedAttemptWithNoData() {
        assertEquals(BlockStatus.UNREACHABLE, block(hasCache = false, attempted = true, rowsEmpty = true))
        assertEquals(BlockStatus.UNREACHABLE, block(ok = false, cachedEmpty = true, attempted = true, rowsEmpty = true))
    }

    @Test fun cachedData_isOkImmediately_beforeAnyAttempt() = assertEquals(BlockStatus.OK, block(attempted = false))

    @Test fun cachedEmptyOk_isEmptyImmediately() = assertEquals(BlockStatus.EMPTY, block(cachedEmpty = true, attempted = false, rowsEmpty = true))

    @Test fun ok_whenFailedButOldDataKept() = assertEquals(BlockStatus.OK, block(ok = false))

    @Test fun empty_whenLoadedWithNoRows() = assertEquals(BlockStatus.EMPTY, block(rowsEmpty = true))

    @Test fun empty_whenCacheHasOnlyPastItems() = assertEquals(BlockStatus.EMPTY, block(ok = false, rowsEmpty = true))

    @Test fun ok_whenRows() = assertEquals(BlockStatus.OK, block())

    private fun watching(settingsLoaded: Boolean = true, permitted: Boolean, dismissed: Boolean = false, read: Boolean = true, tilesEmpty: Boolean = true) =
        AgendaStatus.watching(settingsLoaded, permitted, dismissed, read, tilesEmpty)

    @Test fun watching_loading_beforeSettingsLoad() {
        assertEquals(WatchingState.LOADING, watching(settingsLoaded = false, permitted = false))
        assertEquals(WatchingState.LOADING, watching(settingsLoaded = false, permitted = true, tilesEmpty = false))
    }

    @Test fun watching_loading_whilePermittedAndFirstReadPending() =
        assertEquals(WatchingState.LOADING, watching(permitted = true, read = false))

    @Test fun watching_needsPermission_untilDismissed() {
        assertEquals(WatchingState.NEEDS_PERMISSION, watching(permitted = false, read = false))
        assertEquals(WatchingState.DISMISSED, watching(permitted = false, dismissed = true))
    }

    @Test fun watching_permitted_isEmptyOrOk_evenIfOnceDismissed() {
        assertEquals(WatchingState.EMPTY, watching(permitted = true, dismissed = true))
        assertEquals(WatchingState.OK, watching(permitted = true, tilesEmpty = false))
    }
}
