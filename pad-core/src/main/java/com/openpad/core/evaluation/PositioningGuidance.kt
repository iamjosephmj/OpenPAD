package com.openpad.core.evaluation

import com.openpad.core.InternalPadConfig
import com.openpad.core.PadResult

/**
 * Computes user-facing positioning guidance based on the current face detection.
 *
 * Shared between [PadViewModel] (UI mode) and [OpenPadSessionImpl] (headless mode)
 * to ensure consistent guidance messages.
 */
internal object PositioningGuidance {

    fun compute(result: PadResult, config: InternalPadConfig): String? {
        val raw = result.rawFaceDetection ?: return null

        val inOval = result.faceDetection != null
        if (!inOval) {
            val cx = raw.centerX
            val cy = raw.centerY
            return when {
                cy < 0.3f -> "Move down a little"
                cy > 0.7f -> "Move up a little"
                cx < 0.3f -> "Move right a little"
                cx > 0.7f -> "Move left a little"
                else -> "Position your face in the frame"
            }
        }

        val area = raw.area
        return when {
            area > config.positioningMaxFaceArea -> "Too close \u2014 move back a little"
            area < config.positioningMinFaceArea -> "Move a bit closer"
            else -> null
        }
    }
}
