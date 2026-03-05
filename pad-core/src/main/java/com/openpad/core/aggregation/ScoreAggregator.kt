package com.openpad.core.aggregation

import com.openpad.core.depth.DepthResult
import com.openpad.core.device.DeviceDetectionResult
import com.openpad.core.device.ScreenReflectionResult
import com.openpad.core.frequency.FrequencyResult
import com.openpad.core.photometric.PhotometricResult
import com.openpad.core.signals.TemporalFeatures
import com.openpad.core.texture.TextureResult

/** Layer 6 interface: fuses scores from all analysis layers. */
interface ScoreAggregator {
    /**
     * Classify a single frame by fusing all available signals.
     * @return Per-frame PAD status.
     */
    fun classify(
        textureResult: TextureResult?,
        depthResult: DepthResult?,
        frequencyResult: FrequencyResult?,
        deviceDetectionResult: DeviceDetectionResult?,
        photometricResult: PhotometricResult?,
        temporalFeatures: TemporalFeatures?,
        screenReflectionResult: ScreenReflectionResult? = null
    ): PadStatus
}
