package com.openpad.core.aggregation

import com.openpad.core.PadConfig
import com.openpad.core.depth.DepthResult
import com.openpad.core.device.DeviceDetectionResult
import com.openpad.core.frequency.FrequencyResult
import com.openpad.core.photometric.PhotometricResult
import com.openpad.core.signals.TemporalFeatures
import com.openpad.core.texture.TextureResult
import timber.log.Timber

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
 * 5. Device gate (SSD MobileNet) → SPOOF_SUSPECTED
 * 6. Frequency gate (moire + LBP screen) → SPOOF_SUSPECTED
 * 7. Texture gate (MiniFASNet) → SPOOF_SUSPECTED
 * 8. CDCN depth gate → SPOOF_SUSPECTED
 * 9. Photometric gate → SPOOF_SUSPECTED
 * 10. All pass → LIVE
 * 11. Fallback → ANALYZING
 */
class WeightedAggregator(
    private val config: PadConfig = PadConfig.Default
) : ScoreAggregator {

    override fun classify(
        textureResult: TextureResult?,
        depthResult: DepthResult?,
        frequencyResult: FrequencyResult?,
        deviceDetectionResult: DeviceDetectionResult?,
        photometricResult: PhotometricResult?,
        temporalFeatures: TemporalFeatures?
    ): PadStatus {
        val features = temporalFeatures ?: return PadStatus.ANALYZING

        // Rule 1: Not enough data
        if (features.framesCollected < config.minFramesForDecision) {
            Timber.tag(TAG).v("Classify → ANALYZING (frames=%d < min=%d)",
                features.framesCollected, config.minFramesForDecision)
            return PadStatus.ANALYZING
        }

        // Rule 2: No face
        if (!features.faceDetected) {
            Timber.tag(TAG).v("Classify → NO_FACE (not detected)")
            return PadStatus.NO_FACE
        }

        // Rule 3: Low face confidence
        if (features.faceConfidence < config.minFaceConfidence) {
            Timber.tag(TAG).v("Classify → NO_FACE (conf=%.3f < min=%.3f)",
                features.faceConfidence, config.minFaceConfidence)
            return PadStatus.NO_FACE
        }

        // Rule 4: Continuous face presence
        if (features.consecutiveFaceFrames < config.minConsecutiveFaceFrames) {
            Timber.tag(TAG).v("Classify → ANALYZING (consec=%d < min=%d)",
                features.consecutiveFaceFrames, config.minConsecutiveFaceFrames)
            return PadStatus.ANALYZING
        }

        // Rule 5: Device gate (SSD MobileNet) — phone/laptop/tv overlapping face
        val deviceFlagged = deviceDetectionResult != null &&
            deviceDetectionResult.deviceDetected &&
            deviceDetectionResult.overlapWithFace &&
            deviceDetectionResult.maxConfidence >= config.deviceConfidenceThreshold
        if (deviceFlagged) {
            Timber.tag(TAG).d(
                "Classify → SPOOF_SUSPECTED [device gate] (%s conf=%.3f overlap=true)",
                deviceDetectionResult!!.deviceClass, deviceDetectionResult.maxConfidence
            )
            return PadStatus.SPOOF_SUSPECTED
        }

        // Rule 6: Frequency gate (FFT moire + LBP screen pattern)
        if (frequencyResult != null) {
            val moireFlagged = frequencyResult.moireScore >= config.moireThreshold
            val lbpFlagged = frequencyResult.lbpScreenScore >= config.lbpScreenThreshold
            if (moireFlagged && lbpFlagged) {
                Timber.tag(TAG).d(
                    "Classify → SPOOF_SUSPECTED [frequency gate] (moire=%.3f lbp=%.3f)",
                    frequencyResult.moireScore, frequencyResult.lbpScreenScore
                )
                return PadStatus.SPOOF_SUSPECTED
            }
        }

        // Rule 7: Texture gate (MiniFASNet)
        val textureGenuine = textureResult?.genuineScore ?: 0.5f
        val hasTextureData = textureResult != null
        val texturePasses = textureGenuine >= config.textureGenuineThreshold
        if (hasTextureData && !texturePasses) {
            Timber.tag(TAG).d("Classify → SPOOF_SUSPECTED [texture gate] (genuine=%.3f < threshold=%.3f)",
                textureGenuine, config.textureGenuineThreshold)
            return PadStatus.SPOOF_SUSPECTED
        }

        // Rule 8: CDCN depth gate (only when CDCN result is available)
        val mn3Score = depthResult?.mn3RealScore
        val cdcnScore = depthResult?.cdcnDepthScore
        val hasCdcnData = cdcnScore != null
        if (hasCdcnData && cdcnScore!! < config.depthFlatnessThreshold) {
            Timber.tag(TAG).d(
                "Classify → SPOOF_SUSPECTED [CDCN gate] (cdcn=%.3f < threshold=%.3f)",
                cdcnScore, config.depthFlatnessThreshold
            )
            return PadStatus.SPOOF_SUSPECTED
        }

        // Rule 9: Photometric gate — unnaturally low lighting/surface scores
        if (photometricResult != null &&
            photometricResult.combinedScore < config.photometricMinScore
        ) {
            Timber.tag(TAG).d(
                "Classify → SPOOF_SUSPECTED [photometric gate] (combined=%.3f < threshold=%.3f)",
                photometricResult.combinedScore, config.photometricMinScore
            )
            return PadStatus.SPOOF_SUSPECTED
        }

        // Rule 10: All available signals pass → LIVE
        if (texturePasses) {
            Timber.tag(TAG).d(
                "Classify → LIVE (texture=%.3f, mn3=%s, cdcn=%s)",
                textureGenuine,
                mn3Score?.let { "%.3f".format(it) } ?: "n/a",
                cdcnScore?.let { "%.3f".format(it) } ?: "n/a"
            )
            return PadStatus.LIVE
        }

        // Rule 11: Fallback
        Timber.tag(TAG).v("Classify → ANALYZING (fallback)")
        return PadStatus.ANALYZING
    }

    /**
     * Compute the unified ML aggregate score from the 4 ML model signals.
     *
     * When CDCN is available: texture*0.15 + mn3*0.20 + cdcn*0.55 + device*0.10
     * When CDCN not available: redistribute CDCN weight to texture (2/3) and MN3 (1/3).
     *
     * Classical signals (frequency, photometric) act as gates in [classify] but do not
     * contribute to the continuous aggregate score, which is used for challenge evaluation.
     */
    fun computeAggregateScore(
        textureResult: TextureResult?,
        depthResult: DepthResult?,
        deviceDetectionResult: DeviceDetectionResult?
    ): Float {
        val textureScore = textureResult?.genuineScore ?: 0.5f
        val mn3Score = depthResult?.mn3RealScore ?: 0.5f
        val cdcnScore = depthResult?.cdcnDepthScore
        val deviceScore = 1f - (deviceDetectionResult?.spoofScore ?: 0f)

        return if (cdcnScore != null) {
            // Full ensemble: all 4 ML models
            val totalWeight = config.textureWeight + config.mn3Weight + config.cdcnWeight + config.deviceWeight
            (textureScore * config.textureWeight +
                mn3Score * config.mn3Weight +
                cdcnScore * config.cdcnWeight +
                deviceScore * config.deviceWeight) / totalWeight
        } else {
            // No CDCN: redistribute CDCN weight between texture (2/3) and MN3 (1/3)
            val cdcnRedist = config.cdcnWeight
            val effectiveTextureWeight = config.textureWeight + cdcnRedist * 2f / 3f
            val effectiveMn3Weight = config.mn3Weight + cdcnRedist * 1f / 3f
            val totalWeight = effectiveTextureWeight + effectiveMn3Weight + config.deviceWeight
            (textureScore * effectiveTextureWeight +
                mn3Score * effectiveMn3Weight +
                deviceScore * config.deviceWeight) / totalWeight
        }
    }

    companion object {
        private const val TAG = "PAD"
    }
}
