package com.nickesh.tvlauncher.domain.settings

import com.nickesh.tvlauncher.data.settings.Settings

data class SettingsDraft(val settings: Settings, val screensaverAfterMinutes: Int?)

/** Rules over the fields the Settings draft owns; Background and other live writes are excluded. */
object SettingsDraftRules {
    fun merge(saved: Settings, draft: Settings): Settings = saved.copy(
        name = draft.name,
        homeCity = draft.homeCity,
        homeZone = draft.homeZone,
        homeLat = draft.homeLat,
        homeLon = draft.homeLon,
        clocks = draft.clocks,
        unit = draft.unit,
        use24h = draft.use24h,
        accentArgb = draft.accentArgb,
        screensaverDim = draft.screensaverDim,
        screensaverSeconds = draft.screensaverSeconds,
    )

    fun isDirty(saved: Settings, draft: Settings, savedMinutes: Int?, draftMinutes: Int?): Boolean =
        merge(saved, draft) != saved || savedMinutes != draftMinutes
}
