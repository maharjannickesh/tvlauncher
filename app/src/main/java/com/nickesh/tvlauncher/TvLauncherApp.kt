package com.nickesh.tvlauncher

import android.app.Application

class TvLauncherApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
