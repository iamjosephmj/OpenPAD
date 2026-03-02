package com.openpad.core.detection

/**
 * Single face detection result from Layer 1.
 *
 * @param confidence Detection confidence [0..1].
 * @param bbox Bounding box in normalized coordinates [0..1].
 * @param keypoints 6 facial keypoints (right eye, left eye, nose, mouth, right ear, left ear)
 *                  in normalized coordinates. May be empty if keypoints are not available.
 */
data class FaceDetection(
    val confidence: Float,
    val bbox: BBox,
    val keypoints: List<PointF> = emptyList()
) {
    /** Normalized point (x, y in [0..1]). */
    data class PointF(val x: Float, val y: Float)

    /** Axis-aligned bounding box in normalized [0..1] coordinates. */
    data class BBox(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float
    ) {
        fun width(): Float = right - left
        fun height(): Float = bottom - top
    }

    val centerX: Float get() = (bbox.left + bbox.right) / 2f
    val centerY: Float get() = (bbox.top + bbox.bottom) / 2f
    val area: Float get() = bbox.width() * bbox.height()
}
