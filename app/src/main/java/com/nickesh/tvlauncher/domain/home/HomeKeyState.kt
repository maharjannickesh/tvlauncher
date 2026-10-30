package com.nickesh.tvlauncher.domain.home

/** Answers whether the Home key accessibility service is switched on in system settings. */
object HomeKeyState {
    const val PACKAGE = "com.nickesh.tvlauncher"
    const val SERVICE = "com.nickesh.tvlauncher.HomeKeyService"

    /** True iff accessibility is on and the enabled-services list names our component. */
    fun isEnabled(enabledServices: String?, accessibilityEnabled: Int): Boolean {
        if (accessibilityEnabled != 1 || enabledServices.isNullOrEmpty()) return false
        return enabledServices.split(':').any { entry ->
            val slash = entry.trim().indexOf('/')
            if (slash < 0) return@any false
            val pkg = entry.trim().substring(0, slash)
            var cls = entry.trim().substring(slash + 1)
            if (cls.startsWith('.')) cls = pkg + cls
            pkg == PACKAGE && cls == SERVICE
        }
    }
}
