package com.openpad.core.replay

/**
 * Result from the replay-attack spoof detector.
 *
 * A MobileNetV2-based binary classifier that distinguishes live faces
 * from phone/tablet screen replay attacks by detecting screen artifacts
 * (moiré patterns, color shifts, reduced dynamic range).
 *
 * @param spoofScore Probability that the frame is a replay attack [0..1]. Higher = more likely spoof.
 * @param isSpoof Whether the score exceeds the detection threshold.
 */
data class ReplaySpoofResult(
    val spoofScore: Float,
    val isSpoof: Boolean
)
