package com.openpad.core.signals

/**
 * Layer 4 output: temporal signal features accumulated over a sliding window.
 */
data class TemporalFeatures(
    val faceDetected: Boolean,
    val faceConfidence: Float,
    val faceBboxCenterX: Float,
    val faceBboxCenterY: Float,
    val faceBboxArea: Float,
    val headMovementVariance: Float,
    val faceSizeStability: Float,
    val blinkDetected: Boolean,
    val framesCollected: Int,
    val frameSimilarity: Float,
    val consecutiveFaceFrames: Int,
    val movementSmoothness: Float
)
