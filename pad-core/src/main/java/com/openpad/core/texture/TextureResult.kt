package com.openpad.core.texture

/**
 * Layer 2 output: texture-based liveness scores from MiniFASNet.
 *
 * @param genuineScore Probability the face is genuine [0..1].
 * @param spoofScore Probability the face is a spoof [0..1].
 * @param backgroundScore Probability the crop is background [0..1].
 * @param reflectionProbeVariance Variance of scores across 2x2 spatial grid around face.
 * @param reflectionProbeMinScore Minimum score in the spatial grid.
 */
data class TextureResult(
    val genuineScore: Float,
    val spoofScore: Float,
    val backgroundScore: Float,
    val reflectionProbeVariance: Float = 0f,
    val reflectionProbeMinScore: Float = 0f
)
