package com.openpad.core.depth

/**
 * Result from the cascaded depth analysis pipeline (MN3 fast gate + CDCN depth map).
 *
 * MN3 scores are always present when a face is detected (runs synchronously every frame).
 * CDCN scores are nullable — only available when MN3 passed the gate AND the pipelined
 * CDCN result is ready (1-frame latency).
 *
 * @param mn3RealScore MN3 softmax real probability [0..1]. Higher = more likely real.
 * @param mn3SpoofScore MN3 softmax spoof probability [0..1].
 * @param cdcnDepthScore Mean of CDCN 32x32 depth map [0..1]. Null if CDCN not run/ready.
 * @param cdcnDepthVariance Variance across depth map quadrants. Null if CDCN not run/ready.
 * @param cdcnDepthMapMean Raw mean of depth map. Null if CDCN not run/ready.
 * @param cdcnTriggered Whether CDCN was fired this frame (MN3 passed gate).
 * @param cdcnAvailable Whether a CDCN result is available (from previous frame pipeline).
 * @param depthScore Best available combined score: CDCN if available, else MN3.
 * @param depthVariance Backward-compat variance field.
 * @param depthMapMean Backward-compat raw mean field.
 */
data class DepthResult(
    val mn3RealScore: Float,
    val mn3SpoofScore: Float,
    val cdcnDepthScore: Float? = null,
    val cdcnDepthVariance: Float? = null,
    val cdcnDepthMapMean: Float? = null,
    val cdcnTriggered: Boolean = false,
    val cdcnAvailable: Boolean = false,
    val depthScore: Float,
    val depthVariance: Float = 0f,
    val depthMapMean: Float = 0f,
    val depthCharacteristics: DepthCharacteristics? = null
) {
    companion object {
        /** Result when only MN3 ran (CDCN not yet available or not triggered). */
        fun fromMn3Only(
            mn3Real: Float,
            mn3Spoof: Float,
            cdcnTriggered: Boolean = false
        ) = DepthResult(
            mn3RealScore = mn3Real,
            mn3SpoofScore = mn3Spoof,
            cdcnTriggered = cdcnTriggered,
            cdcnAvailable = false,
            depthScore = mn3Real,
            depthVariance = 0f,
            depthMapMean = mn3Real
        )

        /** Result when both MN3 and CDCN results are available. */
        fun fromBoth(
            mn3Real: Float,
            mn3Spoof: Float,
            cdcnScore: Float,
            cdcnVariance: Float,
            cdcnMean: Float,
            characteristics: DepthCharacteristics? = null
        ) = DepthResult(
            mn3RealScore = mn3Real,
            mn3SpoofScore = mn3Spoof,
            cdcnDepthScore = cdcnScore,
            cdcnDepthVariance = cdcnVariance,
            cdcnDepthMapMean = cdcnMean,
            cdcnTriggered = true,
            cdcnAvailable = true,
            depthScore = cdcnScore,
            depthVariance = cdcnVariance,
            depthMapMean = cdcnMean,
            depthCharacteristics = characteristics
        )
    }
}
