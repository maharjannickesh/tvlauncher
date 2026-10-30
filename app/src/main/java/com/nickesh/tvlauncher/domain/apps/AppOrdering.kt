package com.nickesh.tvlauncher.domain.apps

data class InstalledApp(val packageName: String, val label: String)

object AppOrdering {
    /** Installed favourites in favourites order, then the rest sorted by label (case-insensitive), capped at [max]. */
    fun order(installed: List<InstalledApp>, favourites: List<String>, max: Int = 8): List<InstalledApp> {
        val byPackage = installed.associateBy { it.packageName }
        val favs = favourites.distinct().mapNotNull { byPackage[it] }
        val favSet = favs.mapTo(HashSet()) { it.packageName }
        val rest = installed.filter { it.packageName !in favSet }
            .sortedBy { it.label.lowercase() }
        return (favs + rest).take(max)
    }
}
