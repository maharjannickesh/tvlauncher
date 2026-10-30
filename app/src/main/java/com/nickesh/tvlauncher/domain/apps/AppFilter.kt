package com.nickesh.tvlauncher.domain.apps

object AppFilter {
    fun <T> filter(apps: List<T>, query: String, label: (T) -> String): List<T> {
        val q = query.trim()
        return if (q.isEmpty()) apps else apps.filter { label(it).contains(q, ignoreCase = true) }
    }
}
