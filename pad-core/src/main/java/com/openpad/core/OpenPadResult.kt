package com.openpad.core

/**
 * Result returned by the SDK after a liveness check completes.
 *
 * @property isLive Whether the subject was determined to be a live person.
 * @property confidence Aggregate confidence score in the range [0.0, 1.0].
 * @property durationMs Total wall-clock time of the verification flow in milliseconds.
 * @property spoofAttempts Number of spoof attempts detected during this session.
 */
data class OpenPadResult(
    val isLive: Boolean,
    val confidence: Float,
    val durationMs: Long,
    val spoofAttempts: Int
)
