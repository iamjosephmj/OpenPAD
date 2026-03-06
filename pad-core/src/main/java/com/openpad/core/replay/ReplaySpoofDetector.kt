package com.openpad.core.replay

import android.graphics.Bitmap
import com.openpad.core.detection.FaceDetection

/**
 * Detects phone/tablet screen replay attacks on a camera frame.
 */
interface ReplaySpoofDetector {
    /**
     * Analyze a frame for replay attack artifacts.
     * @param bitmap Full camera frame.
     * @param faceBbox Normalized face bounding box (0..1 coordinates).
     * @return Detection result with spoof score.
     */
    fun analyze(bitmap: Bitmap, faceBbox: FaceDetection.BBox): ReplaySpoofResult

    fun close()
}
