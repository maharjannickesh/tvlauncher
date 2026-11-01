package com.nickesh.tvlauncher.data.watchnext

data class WatchNextRow(
    val id: Long,
    val packageName: String,
    val title: String,
    val season: String?,
    val episode: String?,
    val type: Int,
    val lastEngagedMs: Long,
    val positionMs: Long?,
    val durationMs: Long?,
    val posterUri: String?,
    val intentUri: String?,
    val browsable: Boolean,
)
