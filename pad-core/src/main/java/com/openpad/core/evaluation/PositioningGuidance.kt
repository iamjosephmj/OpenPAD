package com.openpad.core.evaluation

import android.content.Context
import com.openpad.core.InternalPadConfig
import com.openpad.core.PadResult
import com.openpad.core.R

internal object PositioningGuidance {

    fun compute(result: PadResult, config: InternalPadConfig, context: Context): String? {
        val raw = result.rawFaceDetection ?: return null

        val inOval = result.faceDetection != null
        if (!inOval) {
            val cx = raw.centerX
            val cy = raw.centerY
            return when {
                cy < 0.3f -> context.getString(R.string.pad_guidance_move_down)
                cy > 0.7f -> context.getString(R.string.pad_guidance_move_up)
                cx < 0.3f -> context.getString(R.string.pad_guidance_move_right)
                cx > 0.7f -> context.getString(R.string.pad_guidance_move_left)
                else -> context.getString(R.string.pad_guidance_position_face)
            }
        }

        val area = raw.area
        return when {
            area > config.positioningMaxFaceArea -> context.getString(R.string.pad_guidance_too_close)
            area < config.positioningMinFaceArea -> context.getString(R.string.pad_guidance_move_closer)
            else -> null
        }
    }
}
