package com.openpad.core

import android.app.ActivityManager
import android.content.Context

/**
 * Detects device hardware capabilities and maps them to a performance tier.
 * Used by [OpenPadConfig.forDevice] to auto-select an appropriate config preset.
 */
internal object DeviceCapabilityDetector {

    enum class Tier { LOW, MID, HIGH }

    private const val LOW_RAM_BYTES = 3L * 1024 * 1024 * 1024
    private const val HIGH_RAM_BYTES = 6L * 1024 * 1024 * 1024
    private const val LOW_CORES = 4
    private const val HIGH_CORES = 7

    fun detect(context: Context): Tier {
        val cores = Runtime.getRuntime().availableProcessors()
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(memInfo)
        val totalRam = memInfo.totalMem

        return when {
            cores <= LOW_CORES || totalRam <= LOW_RAM_BYTES -> Tier.LOW
            cores >= HIGH_CORES && totalRam >= HIGH_RAM_BYTES -> Tier.HIGH
            else -> Tier.MID
        }
    }
}
