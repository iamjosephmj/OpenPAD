package com.openpad.core

import android.graphics.Bitmap
import com.openpad.core.depth.DepthCharacteristics

/**
 * Result returned by the SDK after a liveness check completes.
 *
 * @property isLive Whether the subject was determined to be a live person.
 * @property confidence Aggregate confidence score in the range [0.0, 1.0].
 * @property durationMs Total wall-clock time of the verification flow in milliseconds.
 * @property spoofAttempts Number of spoof attempts detected during this session.
 * @property depthCharacteristics Averaged 3D depth statistics from the CDCN depth map
 *   across hold-phase frames. Null if CDCN was never triggered.
 * @property faceAtNormalDistance Face crop captured at normal distance (before challenge).
 * @property faceAtCloseDistance Face crop captured at close distance (hold complete).
 */
data class OpenPadResult(
    val isLive: Boolean,
    val confidence: Float,
    val durationMs: Long,
    val spoofAttempts: Int,
    val depthCharacteristics: DepthCharacteristics? = null,
    val faceAtNormalDistance: Bitmap? = null,
    val faceAtCloseDistance: Bitmap? = null
)
