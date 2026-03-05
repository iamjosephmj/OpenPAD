package com.openpad.core.aggregation

import android.util.Log
import com.openpad.core.InternalPadConfig
import com.openpad.core.depth.DepthResult
import com.openpad.core.device.DeviceDetectionResult
import com.openpad.core.device.ScreenReflectionResult
import com.openpad.core.frequency.FrequencyResult
import com.openpad.core.photometric.PhotometricResult
import com.openpad.core.signals.TemporalFeatures
import com.openpad.core.texture.TextureResult

/**
 * Unified score fusion + decision gate.
 *
 * Fuses ML models and classical signal processing into a single classification:
 * - MiniFASNet (texture): surface micro-texture discrimination
 * - MN3 (binary classifier): fast depth pre-filter
 * - CDCN (depth map): primary depth discriminator
 * - SSD MobileNet (device detection): detects replay devices
 * - FFT moire + LBP (frequency): detects screen pixel patterns
 * - Photometric analysis: detects unnatural lighting/surface properties
 *
 * Pipeline (evaluated in order):
 * 1. Not enough frames → ANALYZING
 * 2. No face detected → NO_FACE
 * 3. Low face confidence → NO_FACE
 * 4. Insufficient consecutive face frames → ANALYZING
 * 5a. Static frame gate (high similarity) → SPOOF_SUSPECTED
 * 5b. Low motion gate (no micro-movements) → SPOOF_SUSPECTED
 * 6. Device gate (SSD MobileNet) → SPOOF_SUSPECTED
 * 6b. Screen reflection gate (YOLOv5n) → SPOOF_SUSPECTED
 * 7. Frequency gate (moire + LBP screen) → SPOOF_SUSPECTED
 * 8. Texture gate (MiniFASNet) → SPOOF_SUSPECTED
 * 9. CDCN depth gate → SPOOF_SUSPECTED
 * 10. Photometric gate → SPOOF_SUSPECTED
 * 11. All pass → LIVE
 * 12. Fallback → ANALYZING
 */
class WeightedAggregator(
    private val config: InternalPadConfig = InternalPadConfig.Default
) : ScoreAggregator {

    override fun classify(
        textureResult: TextureResult?,
        depthResult: DepthResult?,
        frequencyResult: FrequencyResult?,
        deviceDetectionResult: DeviceDetectionResult?,
        photometricResult: PhotometricResult?,
        temporalFeatures: TemporalFeatures?,
        screenReflectionResult: ScreenReflectionResult?
    ): PadStatus {
        val features = temporalFeatures ?: return PadStatus.ANALYZING

        // Rule 1: Not enough data
        if (features.framesCollected < config.minFramesForDecision) {
            return PadStatus.ANALYZING
        }

        // Rule 2: No face
        if (!features.faceDetected) {
            return PadStatus.NO_FACE
        }

        // Rule 3: Low face confidence
        if (features.faceConfidence < config.minFaceConfidence) {
            return PadStatus.NO_FACE
        }

        // Rule 4: Continuous face presence
        if (features.consecutiveFaceFrames < config.minConsecutiveFaceFrames) {
            return PadStatus.ANALYZING
        }

        // Rule 5a: Static frame gate — high frame similarity = still photo
        if (features.frameSimilarity >= config.staticFrameThreshold) {
            return PadStatus.SPOOF_SUSPECTED
        }

        // Rule 5b: Low motion gate — no involuntary micro-movements
        if (features.headMovementVariance < config.minMotionVariance) {
            return PadStatus.SPOOF_SUSPECTED
        }

        // Rule 6: Device gate (SSD MobileNet) — phone/laptop/tv overlapping face
        val deviceFlagged = deviceDetectionResult != null &&
            deviceDetectionResult.deviceDetected &&
            deviceDetectionResult.overlapWithFace &&
            deviceDetectionResult.maxConfidence >= config.deviceConfidenceThreshold
        if (deviceFlagged) {
            return PadStatus.SPOOF_SUSPECTED
        }

        // Rule 6b: Screen reflection gate (YOLOv5n) — multiple PAD-specific signals
        val screenFlagged = screenReflectionResult != null &&
            screenReflectionResult.spoofSignalCount >= config.screenReflectionMinSignals &&
            screenReflectionResult.maxConfidence >= config.screenReflectionConfidenceThreshold
        if (screenFlagged) {
            Log.e(TAG, "AGG_SCREEN_GATE: signals=${screenReflectionResult!!.spoofSignalCount} " +
                "maxConf=${"%.3f".format(screenReflectionResult.maxConfidence)} " +
                "spoofScore=${"%.3f".format(screenReflectionResult.spoofScore)}")
            return PadStatus.SPOOF_SUSPECTED
        }

        // Rule 7: Frequency gate (FFT moire + LBP screen pattern)
        if (frequencyResult != null) {
            val moireFlagged = frequencyResult.moireScore >= config.moireThreshold
            val lbpFlagged = frequencyResult.lbpScreenScore >= config.lbpScreenThreshold
            if (moireFlagged && lbpFlagged) {
                return PadStatus.SPOOF_SUSPECTED
            }
        }

        // Rule 7: Texture gate (MiniFASNet)
        val textureGenuine = textureResult?.genuineScore ?: 0.5f
        val hasTextureData = textureResult != null
        val texturePasses = textureGenuine >= config.textureGenuineThreshold
        if (hasTextureData && !texturePasses) {
            return PadStatus.SPOOF_SUSPECTED
        }

        // Rule 8: CDCN depth gate (only when CDCN result is available)
        val mn3Score = depthResult?.mn3RealScore
        val cdcnScore = depthResult?.cdcnDepthScore
        val hasCdcnData = cdcnScore != null
        if (hasCdcnData && cdcnScore!! < config.depthFlatnessThreshold) {
            return PadStatus.SPOOF_SUSPECTED
        }

        // Rule 9: Photometric gate — unnaturally low lighting/surface scores
        if (photometricResult != null &&
            photometricResult.combinedScore < config.photometricMinScore
        ) {
            return PadStatus.SPOOF_SUSPECTED
        }

        // Rule 10: All available signals pass → LIVE
        if (texturePasses) {
            return PadStatus.LIVE
        }

        // Rule 11: Fallback
        return PadStatus.ANALYZING
    }

    /**
     * Compute the unified ML aggregate score from the 5 ML model signals.
     *
     * When CDCN is available: texture + mn3 + cdcn + device + screenReflection
     * When CDCN not available: redistribute CDCN weight to texture (2/3) and MN3 (1/3).
     *
     * Classical signals (frequency, photometric) act as gates in [classify] but do not
     * contribute to the continuous aggregate score, which is used for challenge evaluation.
     */
    fun computeAggregateScore(
        textureResult: TextureResult?,
        depthResult: DepthResult?,
        deviceDetectionResult: DeviceDetectionResult?,
        screenReflectionResult: ScreenReflectionResult?
    ): Float {
        val textureScore = textureResult?.genuineScore ?: 0.5f
        val mn3Score = depthResult?.mn3RealScore ?: 0.5f
        val cdcnScore = depthResult?.cdcnDepthScore
        val deviceScore = 1f - (deviceDetectionResult?.spoofScore ?: 0f)
        val screenScore = 1f - (screenReflectionResult?.spoofScore ?: 0f)

        return if (cdcnScore != null) {
            val totalWeight = config.textureWeight + config.mn3Weight + config.cdcnWeight +
                config.deviceWeight + config.screenReflectionWeight
            (textureScore * config.textureWeight +
                mn3Score * config.mn3Weight +
                cdcnScore * config.cdcnWeight +
                deviceScore * config.deviceWeight +
                screenScore * config.screenReflectionWeight) / totalWeight
        } else {
            val cdcnRedist = config.cdcnWeight
            val effectiveTextureWeight = config.textureWeight + cdcnRedist * 2f / 3f
            val effectiveMn3Weight = config.mn3Weight + cdcnRedist * 1f / 3f
            val totalWeight = effectiveTextureWeight + effectiveMn3Weight +
                config.deviceWeight + config.screenReflectionWeight
            (textureScore * effectiveTextureWeight +
                mn3Score * effectiveMn3Weight +
                deviceScore * config.deviceWeight +
                screenScore * config.screenReflectionWeight) / totalWeight
        }
    }

    companion object {
        private const val TAG = "DA8966"
    }
}
