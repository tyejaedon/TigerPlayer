package com.example.tigerplayer.debug

import android.app.Application
import leakcanary.AppWatcher

object LeakCanaryInitializer {
    fun install(app: Application) {
        AppWatcher.manualInstall(app)
    }
}

