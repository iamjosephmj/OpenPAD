package com.openpad.app

import android.app.Application
import com.openpad.core.OpenPad
import com.openpad.core.OpenPadConfig
import com.openpad.core.OpenPadThemeConfig
import timber.log.Timber

class OpenPadApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())

        // Customize the SDK theme before initialization.
        // Every color is an ARGB Long. Override only what you need.
        OpenPad.theme = OpenPadThemeConfig(
            primary = 0xFF2962FF,
            primaryLight = 0xFF448AFF,
            success = 0xFF00C853,
            error = 0xFFD32F2F,
            surface = 0xFF0D1B2A,
            surfaceVariant = 0xFF1B2838,
            onSurface = 0xFFE0E6ED,
            onSurfaceHigh = 0xFFFFFFFF
        )

        OpenPad.initialize(
            context = this,
            config = OpenPadConfig.Default,
            onReady = { Timber.d("OpenPad SDK initialized") },
            onError = { error -> Timber.e("OpenPad init failed: %s", error) }
        )
    }
}
