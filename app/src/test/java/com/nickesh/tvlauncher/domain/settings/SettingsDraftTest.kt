package com.nickesh.tvlauncher.domain.settings

import com.nickesh.tvlauncher.data.settings.Scene
import com.nickesh.tvlauncher.data.settings.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsDraftTest {
    private val saved = Settings(name = "A", scene = Scene.NIGHT, shuffle = false)

    @Test fun mergeKeepsBackgroundFields() {
        val draft = saved.copy(name = "B", scene = Scene.DUSK, shuffle = true, use24h = true)
        val merged = SettingsDraftRules.merge(saved, draft)
        assertEquals("B", merged.name); assertTrue(merged.use24h); assertEquals(Scene.NIGHT, merged.scene); assertFalse(merged.shuffle)
    }
    @Test fun backgroundChange_isNotDirty() { assertFalse(SettingsDraftRules.isDirty(saved, saved.copy(scene = Scene.FOREST), 10, 10)) }
    @Test fun ownedFieldChange_isDirty() { assertTrue(SettingsDraftRules.isDirty(saved, saved.copy(homeCity = "Lisbon"), 10, 10)) }
    @Test fun minutesChange_isDirty() { assertTrue(SettingsDraftRules.isDirty(saved, saved, 10, 5)); assertFalse(SettingsDraftRules.isDirty(saved, saved, null, null)) }
}
