package com.rawvision.camera

import android.app.Application
import com.rawvision.camera.data.AppContainer

/**
 * Application entry point. Holds the single [AppContainer] that wires the
 * database, repositories and storage together (manual dependency injection,
 * no framework needed).
 */
class RawVisionApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
