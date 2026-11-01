package com.nickesh.tvlauncher.data.watchnext

import android.content.Intent

/** Hardening for a Watch Next resume intent, which any installed app can write. */
object ResumeIntents {
    private const val GRANT_FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION or
        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
        Intent.FLAG_GRANT_PREFIX_URI_PERMISSION

    /**
     * Pins [intent] to [packageName]: a component in another package is dropped, the selector is dropped, URI-grant
     * flags are cleared (Home must not pass its own URI permissions on), and it starts in a new task.
     */
    fun sanitize(intent: Intent, packageName: String): Intent {
        val component = intent.component
        if (component != null && component.packageName != packageName) intent.component = null
        intent.selector = null
        intent.removeFlags(GRANT_FLAGS)
        intent.setPackage(packageName)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return intent
    }
}
